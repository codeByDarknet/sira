import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icone, NomIcone } from '../icone/icone';

/**
 * Bouton d'action à icône seule (colonne « Actions » d'un tableau, barre d'outils). Le libellé
 * est lu par les lecteurs d'écran et s'affiche au survol.
 *
 *     <button siraAction="pencil" libelle="Modifier" (click)="modifier(ligne)"></button>
 *     <button siraAction="ban" libelle="Désactiver" ton="danger" (click)="…"></button>
 */
@Component({
  // eslint-disable-next-line @angular-eslint/component-selector -- le bouton reste un vrai `<button>` (ou `<a>`) : le composant s'y greffe
  selector: 'button[siraAction], a[siraAction]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone],
  template: `<sira-icone [nom]="icone()" />`,
  host: {
    class: 'sira-action',
    '[class.sira-action--danger]': "ton() === 'danger'",
    '[class.sira-action--succes]': "ton() === 'succes'",
    '[attr.aria-label]': 'libelle()',
    '[attr.title]': 'libelle()',
    '[attr.type]': 'type()',
  },
})
export class Action {
  readonly icone = input.required<NomIcone>({ alias: 'siraAction' });
  readonly libelle = input.required<string>();
  readonly ton = input<'neutre' | 'danger' | 'succes'>('neutre');
  /** `button` par défaut, pour ne jamais soumettre un formulaire par mégarde. */
  readonly type = input<string | null>('button');
}
