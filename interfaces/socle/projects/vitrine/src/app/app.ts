import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Entete, LienNavigation, Pied } from '@sira/ui';

@Component({
  selector: 'vi-racine',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Entete, Pied],
  template: `<sira-entete service="Vitrine des composants" [liens]="liens" />
    <main id="contenu" class="sira-page sira-conteneur" tabindex="-1"><router-outlet /></main>
    <sira-pied
      >Sira, socle libre d’interopérabilité entre administrations. Licence EUPL 1.2.</sira-pied
    >`,
  host: { class: 'sira-application' },
})
export class App {
  protected readonly liens: LienNavigation[] = [
    { libelle: 'Catalogue', chemin: '/', exact: true },
    { libelle: 'Écran d’exemple', chemin: '/exemple' },
  ];
}
