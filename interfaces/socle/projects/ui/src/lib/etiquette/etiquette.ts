import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Tag } from 'primeng/tag';
import { Icone, NomIcone } from '../icone/icone';

export type EtatEtiquette = 'neutre' | 'succes' | 'erreur' | 'attention' | 'info';

const SEVERITES: Record<EtatEtiquette, 'secondary' | 'success' | 'danger' | 'warn' | 'info'> = {
  neutre: 'secondary',
  succes: 'success',
  erreur: 'danger',
  attention: 'warn',
  info: 'info',
};

const ICONES: Record<EtatEtiquette, NomIcone | null> = {
  neutre: null,
  succes: 'check',
  erreur: 'x',
  attention: 'clock',
  info: 'info',
};

/** Étiquette d'état : le texte dit l'état, la couleur et l'icône ne font que le souligner. */
@Component({
  selector: 'sira-etiquette',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone, Tag],
  template: `<p-tag [severity]="severite()" [rounded]="false" styleClass="sira-etiquette__tag">
    @if (icone(); as nom) {
      <sira-icone [nom]="nom" />
    }
    <ng-content />
  </p-tag>`,
  host: {
    class: 'sira-etiquette',
    '[class.sira-etiquette--succes]': "etat() === 'succes'",
    '[class.sira-etiquette--erreur]': "etat() === 'erreur'",
    '[class.sira-etiquette--attention]': "etat() === 'attention'",
    '[class.sira-etiquette--info]': "etat() === 'info'",
  },
})
export class Etiquette {
  readonly etat = input<EtatEtiquette>('neutre');

  protected readonly icone = computed(() => ICONES[this.etat()]);
  protected readonly severite = computed(() => SEVERITES[this.etat()]);
}
