import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface LienPied {
  libelle: string;
  chemin: string;
}

/** Pied de page : mêmes liens au même endroit sur tous les écrans (aide cohérente). */
@Component({
  selector: 'sira-pied',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `<footer class="sira-pied">
    <div class="sira-conteneur">
      @if (liens().length > 0) {
        <ul class="sira-pied__liens">
          @for (lien of liens(); track lien.chemin) {
            <li>
              <a [routerLink]="lien.chemin">{{ lien.libelle }}</a>
            </li>
          }
        </ul>
      }
      <p><ng-content /></p>
    </div>
  </footer>`,
})
export class Pied {
  readonly liens = input<readonly LienPied[]>([]);
}
