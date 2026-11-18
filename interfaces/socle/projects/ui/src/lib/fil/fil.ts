import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icone } from '../icone/icone';

export interface ElementFil {
  libelle: string;
  /** Absent pour la page en cours, toujours en dernier. */
  chemin?: string;
}

/** Fil d'Ariane : où se trouve l'écran, et comment remonter. */
@Component({
  selector: 'sira-fil',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icone],
  template: `<nav class="sira-fil" aria-label="Fil d'Ariane">
    <ol class="sira-fil__liste">
      @for (element of elements(); track element.libelle) {
        <li>
          @if (!$first) {
            <sira-icone nom="chevron-right" />
          }
          @if (element.chemin) {
            <a [routerLink]="element.chemin">{{ element.libelle }}</a>
          } @else {
            <span aria-current="page">{{ element.libelle }}</span>
          }
        </li>
      }
    </ol>
  </nav>`,
})
export class Fil {
  readonly elements = input.required<readonly ElementFil[]>();
}
