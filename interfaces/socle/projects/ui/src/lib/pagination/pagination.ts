import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Bouton } from '../bouton/bouton';
import { Icone } from '../icone/icone';

/** Pagination simple : précédent, position, suivant. Les pages sont numérotées à partir de 1. */
@Component({
  selector: 'sira-pagination',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Bouton, Icone],
  template: `@if (pages() > 1) {
    <nav class="sira-pagination" [attr.aria-label]="libelle()">
      <div class="sira-pagination__precedent">
        @if (page() > 1) {
          <button siraBouton variante="discret" type="button" (click)="changement.emit(page() - 1)">
            <sira-icone nom="chevron-left" />
            Précédent<span class="sira-masque"> : page {{ page() - 1 }}</span>
          </button>
        }
      </div>
      <p class="sira-pagination__position" aria-current="page">
        Page {{ page() }} sur {{ pages() }}
      </p>
      <div class="sira-pagination__suivant">
        @if (page() < pages()) {
          <button siraBouton variante="discret" type="button" (click)="changement.emit(page() + 1)">
            Suivant<span class="sira-masque"> : page {{ page() + 1 }}</span>
            <sira-icone nom="chevron-right" />
          </button>
        }
      </div>
    </nav>
  }`,
})
export class Pagination {
  readonly page = input.required<number>();
  readonly pages = input.required<number>();
  readonly libelle = input('Pagination');
  /** Numéro de la page demandée. */
  readonly changement = output<number>();
}
