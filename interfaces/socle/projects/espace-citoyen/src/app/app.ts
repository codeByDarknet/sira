import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { Session } from '@sira/coquille';
import {
  BarreHaute,
  BarreVitrine,
  Chargement,
  Entete,
  LienNavigation,
  LienPied,
  LienVitrine,
  Pied,
} from '@sira/ui';

const MENU: LienNavigation[] = [
  { libelle: 'Accueil', chemin: '/mon-espace', icone: 'house' },
  { libelle: 'Demandes', chemin: '/demandes', icone: 'inbox' },
  { libelle: 'Mes accès', chemin: '/acces', icone: 'key-round' },
  { libelle: 'Historique', chemin: '/historique', icone: 'history' },
];
const PUBLICS: LienVitrine[] = [
  { libelle: 'Accueil', chemin: '/' },
  { libelle: 'Comment ça marche', chemin: '/aide' },
  { libelle: 'Vos droits', chemin: '/aide/vos-droits' },
];
const AIDE: LienPied[] = [
  { libelle: 'Comment ça marche', chemin: '/aide' },
  { libelle: 'Vos droits', chemin: '/aide/vos-droits' },
];

@Component({
  selector: 'ec-racine',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterOutlet, BarreHaute, BarreVitrine, Entete, Pied, Chargement],
  template: `@if (!session.prete()) {
      <sira-chargement />
    } @else if (!session.connecte()) {
      <sira-barre-vitrine service="Espace citoyen" [liens]="publics">
        <button
          vitrine-action
          type="button"
          class="sv-bouton sv-bouton--clair"
          (click)="session.seConnecter('/mon-espace')"
        >
          Se connecter
        </button>
      </sira-barre-vitrine>
      <main id="contenu" class="sira-vitrine-page" tabindex="-1"><router-outlet /></main>
      <footer class="sv-pied">
        <div class="sira-vitrine__largeur sv-pied__contenu">
          <img src="assets/sira/marque/sira-logo-clair.png" alt="Sira" width="110" height="37" />
          <p>Vos informations circulent entre administrations, avec votre accord.</p>
          <nav aria-label="Aide">
            @for (lien of aide; track lien.chemin) {
              <a [routerLink]="lien.chemin">{{ lien.libelle }}</a>
            }
          </nav>
        </div>
      </footer>
    } @else {
      <sira-entete
        laterale
        service="Espace citoyen"
        [liens]="liens()"
        [utilisateur]="session.utilisateur()?.nom"
        [connecte]="session.connecte()"
        (deconnexion)="session.seDeconnecter()"
      />
      @if (session.connecte()) {
        <sira-barre-haute
          service="Espace citoyen"
          [utilisateur]="session.utilisateur()?.nom"
          (deconnexion)="session.seDeconnecter()"
        />
      }
      <main id="contenu" class="sira-page sira-conteneur" tabindex="-1"><router-outlet /></main>
      <sira-pied [liens]="aide">
        Sira : vos informations circulent entre administrations avec votre accord.
      </sira-pied>
    }`,
  host: {
    class: 'sira-application',
    '[class.sira-application--laterale]': 'session.connecte()',
    '[class.sira-application--vitrine]': '!session.connecte()',
  },
})
export class App {
  protected readonly session = inject(Session);
  protected readonly aide = AIDE;
  protected readonly publics = PUBLICS;
  /** Le menu n'apparaît qu'une fois connecté : avant, il ne mènerait qu'à l'écran de connexion. */
  protected readonly liens = computed(() => (this.session.connecte() ? MENU : []));

  constructor() {
    // L'accueil est public : un serveur injoignable ne doit pas empêcher de l'afficher.
    this.session.charger().catch(() => undefined);
  }
}
