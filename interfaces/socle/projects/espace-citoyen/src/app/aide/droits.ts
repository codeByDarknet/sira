import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Lien } from '@sira/ui';

/** Aide : les droits du citoyen sur ses informations, et où les exercer. */
@Component({
  selector: 'ec-droits',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Lien],
  templateUrl: './droits.html',
})
export class Droits {}
