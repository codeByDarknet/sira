import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icone, NomIcone } from '../icone/icone';

/** État vide : il dit pourquoi il n'y a rien et ce qui fera apparaître quelque chose. */
@Component({
  selector: 'sira-etat-vide',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone],
  template: `<sira-icone [nom]="icone()" grande />
    <p class="sira-etat-vide__titre">{{ titre() }}</p>
    <div class="sira-etat-vide__corps"><ng-content /></div>`,
  host: { class: 'sira-etat-vide' },
})
export class EtatVide {
  readonly titre = input.required<string>();
  readonly icone = input<NomIcone>('inbox');
}
