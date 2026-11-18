import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { lireProbleme, Session } from '@sira/coquille';
import {
  Alerte,
  Bouton,
  Cellule,
  ColonneTableau,
  DatePipe,
  ElementFil,
  Etiquette,
  Fil,
  Icone,
  Squelette,
  Synthese,
  SyntheseLigne,
  Tableau,
} from '@sira/ui';
import { firstValueFrom } from 'rxjs';
import { Declarations } from '../dossier/declarations';
import { Verifications } from '../dossier/verifications';
import { mentionDossier } from '../parcours/libelles';
import { EtatDemande } from '../parcours/parcours';
import { Agent, adressePieceDeclareeDossier, adressePieceDossier } from './agent';

const DEMANDES: Record<EtatDemande, string> = {
  EN_ATTENTE_ACCORD: 'En attente',
  REFUSEE: 'Refusée',
  EXPIREE: 'Expirée',
  ANNULEE: 'Annulée',
  EN_COURS: 'Acceptée',
  PARTIELLE: 'Acceptée, réponse partielle',
  COMPLETE: 'Acceptée, complète',
};

/**
 * Un dossier vu par l'agent. Il ne consulte aucun registre lui-même : s'il lui manque une
 * vérification, il la redemande au citoyen, qui l'accepte ou non depuis son espace Sira.
 */
@Component({
  selector: 'ps-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Alerte,
    Bouton,
    Cellule,
    DatePipe,
    Declarations,
    Etiquette,
    Fil,
    Icone,
    Squelette,
    Synthese,
    SyntheseLigne,
    Tableau,
    Verifications,
  ],
  templateUrl: './detail.html',
})
export class Detail {
  protected readonly session = inject(Session);
  private readonly agent = inject(Agent);

  /** Identifiant du dossier, lu dans l'adresse. */
  readonly id = input.required<string>();

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'creee_le', libelle: 'Envoyée le' },
    { cle: 'origine', libelle: 'Par' },
    { cle: 'etat', libelle: 'Suite' },
  ];
  protected readonly mention = mentionDossier;

  protected readonly detail = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.agent.lire(params),
  });
  protected readonly probleme = computed(() =>
    this.detail.error() ? lireProbleme(this.detail.error()).message : null,
  );
  protected readonly fil = computed<ElementFil[]>(() => [
    { libelle: 'Dossiers reçus', chemin: '/agent' },
    { libelle: this.detail.hasValue() ? this.detail.value().dossier.numero : 'Dossier' },
  ]);
  protected readonly adressePiece = computed(
    () => (jeu: string) => adressePieceDossier(this.id(), jeu),
  );
  protected readonly adressePieceDeclaree = computed(
    () => (code: string) => adressePieceDeclareeDossier(this.id(), code),
  );

  protected readonly envoi = signal(false);
  protected readonly envoyee = signal(false);
  protected readonly refus = signal<string | null>(null);

  protected suite(etat: EtatDemande): string {
    return DEMANDES[etat];
  }

  protected async redemander(): Promise<void> {
    this.envoi.set(true);
    this.envoyee.set(false);
    this.refus.set(null);
    try {
      await firstValueFrom(this.agent.redemander(this.id()));
      this.envoyee.set(true);
      this.detail.reload();
    } catch (erreur) {
      this.refus.set(lireProbleme(erreur).message);
    }
    this.envoi.set(false);
  }
}
