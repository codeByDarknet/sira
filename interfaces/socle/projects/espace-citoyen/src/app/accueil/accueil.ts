import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Session } from '@sira/coquille';
import { Icone } from '@sira/ui';

/** Accueil public : ce que fait Sira pour le citoyen, et l'entrée vers son espace. */
@Component({
  selector: 'ec-accueil',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icone],
  templateUrl: './accueil.html',
  host: { class: 'sv-pleine' },
})
export class Accueil {
  protected readonly session = inject(Session);

  protected entrer(): void {
    if (!this.session.connecte()) {
      this.session.seConnecter('/mon-espace');
    }
  }
}
