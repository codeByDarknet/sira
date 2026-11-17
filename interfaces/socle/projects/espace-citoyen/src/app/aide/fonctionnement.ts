import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Lien } from '@sira/ui';

/** Aide : le parcours d'une demande, en quatre gestes. */
@Component({
  selector: 'ec-fonctionnement',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Lien],
  templateUrl: './fonctionnement.html',
})
export class Fonctionnement {}
