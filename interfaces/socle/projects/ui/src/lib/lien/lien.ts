import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';
import { Icone, NomIcone } from '../icone/icone';

/**
 * Lien avec icône facultative : `<a siraLien routerLink="/aide">Besoin d'aide ?</a>`.
 * `externe` ouvre un nouvel onglet, coupe le lien avec la page d'origine et le dit à l'usager.
 */
@Component({
  // eslint-disable-next-line @angular-eslint/component-selector -- le lien reste un vrai `<a>` : le composant s'y greffe
  selector: 'a[siraLien]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone],
  template: `@if (icone(); as nom) {
      <sira-icone [nom]="nom" />
    }
    <span><ng-content /></span>
    @if (externe()) {
      <sira-icone nom="external-link" />
      <span class="sira-masque">(nouvelle fenêtre)</span>
    }`,
  host: {
    class: 'sira-lien',
    '[attr.target]': 'externe() ? "_blank" : null',
    '[attr.rel]': 'externe() ? "noopener noreferrer" : null',
  },
})
export class Lien {
  readonly icone = input<NomIcone>();
  readonly externe = input(false, { transform: booleanAttribute });
}
