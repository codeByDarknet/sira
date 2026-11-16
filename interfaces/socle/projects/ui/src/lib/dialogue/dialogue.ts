import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  model,
  input,
  viewChild,
} from '@angular/core';
import { identifiantUnique } from '../commun/identifiant';

/**
 * Fenêtre de confirmation, sur l'élément natif `<dialog>` : le navigateur gère le piège
 * de focus et la touche Échap. À réserver aux actions difficiles à annuler.
 *
 *     <sira-dialogue titre="Retirer l'accès ?" [(ouvert)]="ouvert">
 *       <p>…</p>
 *       <button dialogue-actions siraBouton variante="danger">Retirer l'accès</button>
 *       <button dialogue-actions siraBouton variante="secondaire">Annuler</button>
 *     </sira-dialogue>
 */
@Component({
  selector: 'sira-dialogue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<dialog
    #fenetre
    class="sira-dialogue"
    [attr.aria-labelledby]="idTitre"
    (close)="ouvert.set(false)"
  >
    <h2 class="sira-dialogue__titre" [id]="idTitre" tabindex="-1">{{ titre() }}</h2>
    <div class="sira-dialogue__corps"><ng-content /></div>
    <div class="sira-groupe-boutons"><ng-content select="[dialogue-actions]" /></div>
  </dialog>`,
})
export class Dialogue {
  readonly titre = input.required<string>();
  readonly ouvert = model(false);

  protected readonly idTitre = identifiantUnique('sira-dialogue');
  private readonly fenetre = viewChild.required<ElementRef<HTMLDialogElement>>('fenetre');

  constructor() {
    afterRenderEffect(() => {
      const fenetre = this.fenetre().nativeElement;
      if (this.ouvert() && !fenetre.open) {
        fenetre.showModal();
        // Le titre d'abord : l'usager lit la question avant d'atteindre un bouton.
        fenetre.querySelector<HTMLElement>('.sira-dialogue__titre')?.focus();
      } else if (!this.ouvert() && fenetre.open) {
        fenetre.close();
      }
    });
  }
}
