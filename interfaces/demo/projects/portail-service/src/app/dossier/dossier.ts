import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { lireProbleme } from '@sira/coquille';
import {
  Alerte,
  Bouton,
  Champ,
  DatePipe,
  EtatVide,
  Etapes,
  GroupeFormulaire,
  Icone,
  Saisie,
  Squelette,
  Synthese,
  SyntheseLigne,
} from '@sira/ui';
import { Observable, firstValueFrom } from 'rxjs';
import { Fichier } from '../fichier/fichier';
import { ETAPES } from '../parcours/etapes';
import {
  libelleChamp,
  nomRegistre,
  nonTransmise,
  recue,
  tailleFichier,
  valeurChamp,
} from '../parcours/libelles';
import {
  Dossier,
  Parcours,
  Verification,
  adressePiece,
  adressePieceDeclaree,
} from '../parcours/parcours';
import { periodiquement } from '../parcours/periodique';
import { Declarations } from './declarations';
import { Verifications } from './verifications';

const INTERVALLE = 5000;
/** Borne du serveur pour une pièce jointe. */
export const TAILLE_MAX_PIECE = 2 * 1024 * 1024;

/** Ce qu'un fichier choisi doit respecter avant d'être envoyé ; null s'il convient. */
export function refusPiece(fichier: File): string | null {
  const pdf = fichier.type === 'application/pdf' || /\.pdf$/i.test(fichier.name);
  if (!pdf) {
    return 'Choisissez un fichier PDF.';
  }
  if (fichier.size === 0) {
    return 'Le fichier est vide.';
  }
  return fichier.size > TAILLE_MAX_PIECE ? 'Le fichier dépasse 2 Mo.' : null;
}

function sans<T>(objet: Record<string, T>, cle: string): Record<string, T> {
  return Object.fromEntries(Object.entries(objet).filter(([nom]) => nom !== cle));
}

/** Ce que l'écran dit sous une information du registre, selon où en est sa réponse. */
export function provenance(verification: Verification): string {
  const registre = nomRegistre(verification.registre);
  if (recue(verification)) {
    return `Vérifié par ${registre}`;
  }
  if (nonTransmise(verification)) {
    return `Non transmis par ${registre}. Vous pouvez joindre un justificatif PDF.`;
  }
  return `En attente de ${registre} : l’information arrivera toute seule.`;
}

/**
 * Le dossier du citoyen. Avant le dépôt, c'est le formulaire de la démarche : ce qui vient des
 * registres est verrouillé, la personne ne complète que le reste. Après le dépôt, c'est le
 * récapitulatif. Tant qu'une réponse manque, l'écran se met à jour tout seul.
 */
@Component({
  selector: 'ps-dossier',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    Alerte,
    Bouton,
    Champ,
    DatePipe,
    Declarations,
    EtatVide,
    Etapes,
    Fichier,
    GroupeFormulaire,
    Icone,
    Saisie,
    Squelette,
    Synthese,
    SyntheseLigne,
    Verifications,
  ],
  templateUrl: './dossier.html',
})
export class DossierCitoyen {
  private readonly parcours = inject(Parcours);
  private readonly router = inject(Router);

  protected readonly etapes = ETAPES;
  protected readonly adressePiece = adressePiece;
  protected readonly adressePieceDeclaree = adressePieceDeclaree;
  protected readonly provenance = provenance;
  protected readonly nonTransmise = nonTransmise;
  protected readonly tailleFichier = tailleFichier;

  protected readonly demarche = rxResource({ stream: () => this.parcours.demarche() });
  protected readonly dossier = signal<Dossier | null>(null);
  protected readonly absent = signal(false);
  protected readonly probleme = signal<string | null>(null);
  protected readonly fermeture = signal(false);

  /** Saisies pas encore enregistrées, par code. */
  protected readonly brouillons = signal<Record<string, string>>({});
  /** Code de l'information en cours d'envoi. */
  protected readonly envoi = signal<string | null>(null);
  protected readonly erreurs = signal<Record<string, string>>({});
  protected readonly enregistre = signal<string | null>(null);
  protected readonly depot = signal(false);
  protected readonly refusDepot = signal<string | null>(null);

  /** Les informations des registres, prêtes pour le formulaire. */
  protected readonly informations = computed(() => {
    const dossier = this.dossier();
    if (!dossier) {
      return [];
    }
    const jeux = this.demarche.hasValue() ? this.demarche.value().jeux : [];
    return dossier.verifications.map((verification) => {
      const attendus = jeux.find((jeu) => jeu.code === verification.jeu)?.champs ?? [];
      const recus = verification.champs ?? {};
      const cles = verification.champs ? Object.keys(recus) : attendus;
      return {
        verification,
        registre: nomRegistre(verification.registre),
        champs: cles.map((cle) => ({
          cle,
          libelle: libelleChamp(cle),
          valeur: cle in recus ? valeurChamp(recus[cle] ?? null) : '',
        })),
        substitution:
          dossier.declarations.find((d) => d.code === verification.jeu && d.remplace) ?? null,
      };
    });
  });

  /** Ce que la personne renseigne elle-même, avec ce qu'elle a déjà déclaré. */
  protected readonly aCompleter = computed(() => {
    const dossier = this.dossier();
    const attendues = this.demarche.hasValue() ? this.demarche.value().declarations : [];
    return attendues.map((attendue) => ({
      attendue,
      declaree: dossier?.declarations.find((d) => d.code === attendue.code) ?? null,
    }));
  });

  constructor() {
    void this.actualiser();
    periodiquement(INTERVALLE, () => {
      if (this.dossier()?.etat === 'EN_VERIFICATION' && this.envoi() === null) {
        void this.actualiser();
      }
    });
  }

  protected async actualiser(): Promise<void> {
    try {
      this.dossier.set(await firstValueFrom(this.parcours.dossier()));
      this.probleme.set(null);
    } catch (erreur) {
      const probleme = lireProbleme(erreur);
      if (probleme.statut === 404) {
        this.absent.set(true);
      } else if (!this.dossier()) {
        // Pendant une mise à jour, le dossier déjà affiché reste à l'écran.
        this.probleme.set(probleme.message);
      }
    }
  }

  protected erreur(code: string): string | null {
    return this.erreurs()[code] ?? null;
  }

  protected brouillon(code: string, enregistree: string | null): string {
    return this.brouillons()[code] ?? enregistree ?? '';
  }

  protected modifier(code: string, valeur: string): void {
    this.brouillons.update((brouillons) => ({ ...brouillons, [code]: valeur }));
    this.enregistre.set(null);
  }

  protected async enregistrer(evenement: SubmitEvent, code: string): Promise<void> {
    evenement.preventDefault();
    if (await this.enregistrerTexte(code)) {
      this.enregistre.set(code);
    }
  }

  /** Une saisie vide retire ce qui avait été déclaré. Vrai si tout s'est bien passé. */
  private async enregistrerTexte(code: string): Promise<boolean> {
    const brouillon = this.brouillons()[code];
    if (brouillon === undefined) {
      return true;
    }
    const valeur = brouillon.trim();
    const declaree = this.dossier()?.declarations.find((d) => d.code === code);
    if (!valeur && !declaree) {
      return true;
    }
    const envoye = await this.envoyer(
      code,
      valeur ? this.parcours.saisir(code, valeur) : this.parcours.retirer(code),
    );
    if (envoye) {
      this.brouillons.update((brouillons) => sans(brouillons, code));
    }
    return envoye;
  }

  protected async joindre(code: string, evenement: Event): Promise<void> {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    champ.value = '';
    if (!fichier) {
      return;
    }
    const refus = refusPiece(fichier);
    if (refus) {
      this.erreurs.update((erreurs) => ({ ...erreurs, [code]: refus }));
      return;
    }
    await this.envoyer(code, this.parcours.joindre(code, fichier));
  }

  protected async retirer(code: string): Promise<void> {
    await this.envoyer(code, this.parcours.retirer(code));
  }

  private async envoyer(code: string, requete: Observable<Dossier>): Promise<boolean> {
    this.envoi.set(code);
    this.erreurs.update((erreurs) => sans(erreurs, code));
    try {
      this.dossier.set(await firstValueFrom(requete));
      return true;
    } catch (erreur) {
      const probleme = lireProbleme(erreur);
      const message = probleme.champs['valeur'] ?? probleme.champs['piece'] ?? probleme.message;
      this.erreurs.update((erreurs) => ({ ...erreurs, [code]: message }));
      return false;
    } finally {
      this.envoi.set(null);
    }
  }

  /** « Déposer mon dossier » : les saisies en cours partent d'abord. */
  protected async deposer(): Promise<void> {
    this.depot.set(true);
    this.refusDepot.set(null);
    for (const code of Object.keys(this.brouillons())) {
      if (!(await this.enregistrerTexte(code))) {
        this.refusDepot.set('Corrigez les informations signalées, puis déposez à nouveau.');
        this.depot.set(false);
        return;
      }
    }
    try {
      this.dossier.set(await firstValueFrom(this.parcours.deposer()));
      window.scrollTo({ top: 0 });
    } catch (erreur) {
      this.refusDepot.set(lireProbleme(erreur).message);
    }
    this.depot.set(false);
  }

  protected async terminer(): Promise<void> {
    this.fermeture.set(true);
    try {
      await firstValueFrom(this.parcours.terminer());
    } catch {
      // La session s'éteindra d'elle-même : l'usager peut quitter l'écran.
    }
    await this.router.navigateByUrl('/', { replaceUrl: true });
  }
}
