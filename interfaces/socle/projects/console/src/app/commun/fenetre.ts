import { ChangeDetectionStrategy, Component, booleanAttribute, input, model } from '@angular/core';
import { Dialog } from 'primeng/dialog';

/**
 * Fenêtre d'ajout ou de modification (PrimeNG Dialog) : titre, phrase d'explication, puis le
 * formulaire projeté. L'écran hôte entoure le formulaire d'un `@if` : chaque ouverture repart
 * ainsi d'un formulaire propre.
 *
 *     <co-fenetre titre="Ajouter un membre" intro="…" [(ouvert)]="ajout">
 *       <co-formulaire-membre (termine)="…" (annule)="ajout.set(false)" />
 *     </co-fenetre>
 */
@Component({
  selector: 'co-fenetre',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog],
  template: `<p-dialog
    [styleClass]="
      large() ? 'sira-fenetre-formulaire sira-fenetre-formulaire--large' : 'sira-fenetre-formulaire'
    "
    [header]="titre()"
    [modal]="true"
    [draggable]="false"
    [resizable]="false"
    [closeOnEscape]="true"
    [blockScroll]="true"
    [(visible)]="ouvert"
  >
    @if (intro()) {
      <p class="sira-fenetre-formulaire__intro">{{ intro() }}</p>
    }
    <ng-content />
  </p-dialog>`,
})
export class Fenetre {
  readonly titre = input.required<string>();
  readonly intro = input<string>();
  readonly ouvert = model(false);
  readonly large = input(false, { transform: booleanAttribute });
}
