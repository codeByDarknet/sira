import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {
  CONFIGURATION,
  ROLES_GERER,
  ROLES_LIRE,
  UTILISATEURS_GERER,
  UTILISATEURS_LIRE,
} from '@demo/commun';
import { Session } from '@sira/coquille';
import { BarreHaute, Chargement, Entete, LienNavigation, Pied } from '@sira/ui';

import { APPELS_LIRE, PANNES_GERER, PANNES_LIRE, PERSONNES_LIRE } from './droits';

/** Chaque entrée n'apparaît qu'aux agents dont le rôle donne l'un des droits cités. */
const MENU: (LienNavigation & { droits?: string[] })[] = [
  { libelle: 'Tableau de bord', chemin: '/', icone: 'layout-dashboard', exact: true },
  { libelle: 'Personnes', chemin: '/personnes', icone: 'users', droits: [PERSONNES_LIRE] },
  {
    libelle: 'Pannes',
    chemin: '/pannes',
    icone: 'triangle-alert',
    droits: [PANNES_LIRE, PANNES_GERER],
  },
  { libelle: 'Appels reçus', chemin: '/appels', icone: 'inbox', droits: [APPELS_LIRE] },
  {
    libelle: 'Utilisateurs',
    chemin: '/utilisateurs',
    icone: 'user',
    droits: [UTILISATEURS_LIRE, UTILISATEURS_GERER],
  },
  { libelle: 'Rôles', chemin: '/roles', icone: 'shield-check', droits: [ROLES_LIRE, ROLES_GERER] },
];

@Component({
  selector: 'ra-racine',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, BarreHaute, Entete, Pied, Chargement],
  template: `@if (session.prete()) {
      <sira-entete
        laterale
        plein
        signe="building-2"
        [nom]="configuration.nom"
        [service]="configuration.organisme"
        [liens]="liens()"
        [utilisateur]="session.utilisateur()?.nom"
        [connecte]="session.connecte()"
        (deconnexion)="session.seDeconnecter()"
      />
      @if (session.connecte()) {
        <sira-barre-haute
          [service]="configuration.nom"
          [utilisateur]="session.utilisateur()?.nom"
          (deconnexion)="session.seDeconnecter()"
        />
      }
      <main id="contenu" class="sira-page sira-conteneur" tabindex="-1"><router-outlet /></main>
      <sira-pied>{{ configuration.nom }}. Réservé aux agents du registre.</sira-pied>
    } @else {
      <sira-chargement />
    }`,
  host: { class: 'sira-application sira-application--laterale' },
})
export class App {
  protected readonly configuration = inject(CONFIGURATION);
  protected readonly session = inject(Session);
  protected readonly liens = computed(() =>
    this.session.connecte()
      ? MENU.filter((lien) => !lien.droits || this.session.peut(...lien.droits))
      : [],
  );

  constructor() {
    // Les gardes lisent aussi la session ; une page sans garde (erreur) ne doit pas rester en chargement.
    this.session.charger().catch(() => undefined);
  }
}
