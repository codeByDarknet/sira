import { ChangeDetectionStrategy, Component, Directive, input } from '@angular/core';

/**
 * Liste de synthèse clé / valeur, sur une liste de définitions :
 *
 *     <dl siraSynthese>
 *       <div siraSyntheseLigne cle="Centre d'examen">
 *         Ouagadougou
 *         <a synthese-action routerLink="centre">Modifier<span class="sira-masque"> le centre d'examen</span></a>
 *       </div>
 *     </dl>
 */
@Directive({ selector: 'dl[siraSynthese]', host: { class: 'sira-synthese' } })
export class Synthese {}

@Component({
  // eslint-disable-next-line @angular-eslint/component-selector -- un `div` est la seule balise admise entre `dl` et `dt`
  selector: 'div[siraSyntheseLigne]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<dt class="sira-synthese__cle">{{ cle() }}</dt>
    <dd class="sira-synthese__valeur"><ng-content /></dd>
    <dd class="sira-synthese__action"><ng-content select="[synthese-action]" /></dd>`,
  host: { class: 'sira-synthese__ligne' },
})
export class SyntheseLigne {
  readonly cle = input.required<string>();
}
