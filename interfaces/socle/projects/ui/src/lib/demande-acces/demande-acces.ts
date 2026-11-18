import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { Bouton } from '../bouton/bouton';
import { Code } from '../code/code';
import { formaterJour } from '../commun/dates';
import { Icone, NomIcone } from '../icone/icone';
import { Minuteur } from '../minuteur/minuteur';

/** Forme sous laquelle une information est transmise. */
export type NiveauInformation = 'STATUT' | 'CHAMPS' | 'DOCUMENT';

export interface InformationDemandee {
  libelle: string;
  niveau: NiveauInformation;
  /** Administration qui détient l'information, en clair : « État civil ». */
  source: string;
  /** Précision facultative : les informations transmises, ou ce qui ne l'est pas. */
  precision?: string;
}

export interface DemandeAcces {
  demandeur: { nom: string; organisme?: string };
  /** Pourquoi le service demande ces informations. */
  finalite: string;
  informations: readonly InformationDemandee[];
  /** Durée de l'accès en jours ; 0 pour une seule consultation. */
  dureeJours: number;
  /** Code à comparer avec l'écran du service (demande par code ou par notification). */
  codeVerification?: string;
  /** Date limite pour répondre. */
  expireLe?: string;
}

const FORMES: Record<NiveauInformation, { icone: NomIcone; texte: string; precision?: string }> = {
  STATUT: {
    icone: 'check-check',
    texte: 'Réponse : oui ou non',
    precision: 'Le service ne reçoit pas l’information elle-même.',
  },
  CHAMPS: { icone: 'list', texte: 'Quelques informations' },
  DOCUMENT: { icone: 'file-text', texte: 'Document PDF' },
};

/** Durée de l'accès, en une phrase. */
export function phraseDuree(jours: number, depuis = new Date()): string {
  if (jours <= 0) {
    return 'Une seule consultation, aujourd’hui. Le service ne pourra pas redemander ces informations sans un nouvel accord.';
  }
  const fin = new Date(depuis);
  fin.setDate(fin.getDate() + jours);
  const duree = jours === 1 ? '1 jour' : `${jours} jours`;
  return `Pendant ${duree}, jusqu’au ${formaterJour(fin)}. Vous pouvez retirer cet accès à tout moment.`;
}

/**
 * La demande d'accès, toujours présentée de la même façon : qui demande, pourquoi, quoi et
 * sous quelle forme, pour combien de temps. « Autoriser » et « Refuser » ont le même poids.
 * Le composant n'appelle rien : il émet `autoriser` ou `refuser`.
 */
@Component({
  selector: 'sira-demande-acces',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Bouton, Code, Icone, Minuteur],
  template: `<div class="sira-demande">
    <div class="sira-demande__demandeur">
      <sira-icone nom="building-2" grande />
      <div>
        <p class="sira-demande__nom">{{ demande().demandeur.nom }}</p>
        @if (demande().demandeur.organisme; as organisme) {
          <p>{{ organisme }}</p>
        }
        <p class="sira-demande__enregistre">
          <sira-icone nom="shield-check" />
          Service enregistré auprès de Sira
        </p>
      </div>
    </div>

    <div class="sira-demande__section">
      <div class="sira-demande__titre" role="heading" [attr.aria-level]="niveauTitre()">
        Pourquoi
      </div>
      <p>{{ demande().finalite }}</p>
    </div>

    <div class="sira-demande__section">
      <div class="sira-demande__titre" role="heading" [attr.aria-level]="niveauTitre()">
        Ce que le service recevra
      </div>
      <ul class="sira-demande__informations">
        @for (information of informations(); track information.libelle) {
          <li class="sira-demande__information">
            <p class="sira-demande__libelle">{{ information.libelle }}</p>
            <p class="sira-demande__forme">
              <sira-icone [nom]="information.icone" />
              <span>{{ information.forme }}</span>
            </p>
            @if (information.precision) {
              <p>{{ information.precision }}</p>
            }
            <p class="sira-demande__source">Source : {{ information.source }}</p>
          </li>
        }
      </ul>
    </div>

    <div class="sira-demande__section">
      <div class="sira-demande__titre" role="heading" [attr.aria-level]="niveauTitre()">Durée</div>
      <p>{{ duree() }}</p>
    </div>

    @if (demande().codeVerification; as code) {
      <div class="sira-demande__section sira-demande__verification">
        <div class="sira-demande__titre" role="heading" [attr.aria-level]="niveauTitre()">
          Vérifiez le code
        </div>
        <p>Ce code doit être le même que celui affiché par le service.</p>
        <sira-code [code]="code" />
        <p><strong>Si le code est différent, refusez.</strong></p>
      </div>
    }

    @if (demande().expireLe; as limite) {
      <sira-minuteur [echeance]="limite" (expire)="expiree.set(true)" />
    }

    @if (avecDecision() && !expiree()) {
      <div class="sira-groupe-boutons sira-groupe-boutons--egaux">
        <button siraBouton type="button" [enCours]="enCours()" (click)="autoriser.emit()">
          <sira-icone nom="check" />
          Autoriser
        </button>
        <button siraBouton type="button" [enCours]="enCours()" (click)="refuser.emit()">
          <sira-icone nom="x" />
          Refuser
        </button>
      </div>
      <p class="sira-demande__refus">
        Si vous refusez, le service ne reçoit rien. Vous pourrez fournir vos documents vous-même.
      </p>
    }
  </div>`,
})
export class DemandeAccesVue {
  readonly demande = input.required<DemandeAcces>();
  /** Faux pour une simple consultation (détail d'un accès déjà donné). */
  readonly avecDecision = input(true, { transform: booleanAttribute });
  readonly enCours = input(false, { transform: booleanAttribute });
  readonly niveauTitre = input<2 | 3>(2);
  readonly autoriser = output();
  readonly refuser = output();

  protected readonly expiree = signal(false);
  protected readonly duree = computed(() => phraseDuree(this.demande().dureeJours));
  protected readonly informations = computed(() =>
    this.demande().informations.map((information) => {
      const forme = FORMES[information.niveau];
      return {
        ...information,
        icone: forme.icone,
        forme: forme.texte,
        precision: information.precision ?? forme.precision,
      };
    }),
  );
}
