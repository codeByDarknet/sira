import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { lireProbleme } from '@sira/coquille';
import { Bouton, Champ, DatePipe, Dialogue, Saisie } from '@sira/ui';
import { LigneJournal } from '../commun/modeles';
import { Journal, MOTIF_MAXIMUM, MOTIF_MINIMUM } from './journal';

/** Message d'erreur d'un motif de signalement, ou `null` s'il est recevable. */
export function verifierMotif(motif: string): string | null {
  const longueur = motif.trim().length;
  if (longueur < MOTIF_MINIMUM) {
    return `Expliquez le problème en ${MOTIF_MINIMUM} caractères au moins.`;
  }
  return longueur > MOTIF_MAXIMUM
    ? `Raccourcissez votre message : ${MOTIF_MAXIMUM} caractères au plus.`
    : null;
}

/** « Signaler un problème » sur une ligne de l'historique : la fenêtre s'ouvre dès qu'une ligne est donnée. */
@Component({
  selector: 'ec-signalement',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Bouton, Champ, DatePipe, Dialogue, Saisie],
  template: `<sira-dialogue
    titre="Signaler un problème"
    [ouvert]="ligne() !== null"
    (ouvertChange)="ferme.emit()"
  >
    @if (ligne(); as concernee) {
      <p>
        Accès de <strong>{{ concernee.service.nom }}</strong> du
        {{ concernee.horodatage | siraDate }}
      </p>
      <sira-champ
        libelle="Quel est le problème ?"
        aide="Exemple : je n’ai jamais fait cette démarche."
        identifiant="motif"
        [erreur]="erreur()"
      >
        <textarea
          siraSaisie
          name="motif"
          rows="4"
          [attr.maxlength]="maximum"
          [value]="motif()"
          (input)="motif.set(zone.value)"
          #zone
        ></textarea>
      </sira-champ>
    }
    <button dialogue-actions siraBouton type="button" [enCours]="envoi()" (click)="envoyer()">
      Envoyer
    </button>
    <button dialogue-actions siraBouton variante="secondaire" type="button" (click)="ferme.emit()">
      Annuler
    </button>
  </sira-dialogue>`,
})
export class Signalement {
  private readonly journal = inject(Journal);

  /** Ligne à signaler ; `null` ferme la fenêtre. */
  readonly ligne = input.required<LigneJournal | null>();
  readonly ferme = output();
  /** Émis une fois le signalement enregistré par le serveur. */
  readonly envoye = output();

  protected readonly maximum = MOTIF_MAXIMUM;
  /** Le message et son erreur repartent de zéro à chaque nouvelle ligne. */
  protected readonly motif = linkedSignal({ source: this.ligne, computation: () => '' });
  protected readonly erreur = linkedSignal<LigneJournal | null, string | null>({
    source: this.ligne,
    computation: () => null,
  });
  protected readonly envoi = signal(false);

  protected envoyer(): void {
    const ligne = this.ligne();
    const erreur = verifierMotif(this.motif());
    this.erreur.set(erreur);
    if (erreur || !ligne) {
      return;
    }
    this.envoi.set(true);
    this.journal.signaler(ligne.id, this.motif().trim()).subscribe({
      next: () => {
        this.envoi.set(false);
        this.envoye.emit();
      },
      error: (echec: unknown) => {
        this.envoi.set(false);
        const probleme = lireProbleme(echec);
        this.erreur.set(probleme.champs['motif'] ?? probleme.message);
      },
    });
  }
}
