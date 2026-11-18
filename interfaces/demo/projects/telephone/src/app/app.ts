import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CONFIGURATION } from '@demo/commun';
import { Entete } from '@sira/ui';

@Component({
  selector: 'te-racine',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Entete],
  template: `<sira-entete
      signe="smartphone"
      [nom]="configuration.nom"
      [service]="configuration.organisme"
    />
    <main id="contenu" class="sira-page sira-conteneur sira-conteneur--large" tabindex="-1">
      <router-outlet />
    </main>`,
  host: { class: 'sira-application' },
})
export class App {
  protected readonly configuration = inject(CONFIGURATION);
}
