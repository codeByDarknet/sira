import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DatePipe, Etiquette } from '@sira/ui';
import { EtatRegistre, LIBELLES_DISJONCTEUR } from './reseau';

/** État d'un registre : répond-il, combien de réponses attendent, derniers succès et échec. */
@Component({
  selector: 'co-etat-registre',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, Etiquette],
  template: `<p>
      <sira-etiquette [etat]="libelle().etat">{{ libelle().texte }}</sira-etiquette>
    </p>
    @if (etat().disjoncteur === 'OUVERT') {
      <p>Appels suspendus, reprise automatique.</p>
    }
    <dl class="co-reperes">
      <div>
        <dt>Demande en attente</dt>
        <dd>{{ attente() }}</dd>
      </div>
      <div>
        <dt>Dernière réponse</dt>
        <dd>{{ (etat().dernier_succes_le | siraDate) || 'Aucune' }}</dd>
      </div>
      <div>
        <dt>Dernier échec</dt>
        <dd>{{ (etat().dernier_echec_le | siraDate) || 'Aucun' }}</dd>
      </div>
    </dl>`,
})
export class EtatRegistreVue {
  readonly etat = input.required<EtatRegistre>();

  protected readonly libelle = computed(() => LIBELLES_DISJONCTEUR[this.etat().disjoncteur]);
  protected readonly attente = computed(() => {
    const nombre = this.etat().en_file;
    return nombre === 0 ? 'Aucune' : String(nombre);
  });
}
