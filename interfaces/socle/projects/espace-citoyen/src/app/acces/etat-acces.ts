import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Etiquette } from '@sira/ui';
import { EtatAutorisation } from '../commun/modeles';

/** État d'un accès, en toutes lettres. */
@Component({
  selector: 'ec-etat-acces',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Etiquette],
  template: `@switch (etat()) {
    @case ('ACTIVE') {
      <sira-etiquette etat="succes">En cours</sira-etiquette>
    }
    @case ('RETIREE') {
      <sira-etiquette>Retiré par vous</sira-etiquette>
    }
    @default {
      <sira-etiquette>Terminé</sira-etiquette>
    }
  }`,
})
export class EtatAcces {
  readonly etat = input.required<EtatAutorisation>();
}
