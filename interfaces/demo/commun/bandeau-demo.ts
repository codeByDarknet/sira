import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Bandeau } from '@sira/ui';

/** Bandeau permanent des écrans de démonstration : personne ne doit les prendre pour un vrai service. */
@Component({
  selector: 'demo-bandeau',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Bandeau],
  template: ``,
})
export class BandeauDemo {}
