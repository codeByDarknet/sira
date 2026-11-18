import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import {
  CONFIGURATION,
  ROLES_GERER,
  ROLES_LIRE,
  UTILISATEURS_GERER,
  UTILISATEURS_LIRE,
} from '@demo/commun';
import { Session } from '@sira/coquille';
import { BarreVitrine, Entete, LienNavigation, LienPied, LienVitrine, Pied } from '@sira/ui';

/** Chaque entrée n'apparaît qu'aux agents dont le rôle donne l'un des droits cités. */
const MENU_AGENT: (LienNavigation & { droits: string[] })[] = [
  { libelle: 'Dossiers reçus', chemin: '/agent', exact: true, droits: ['DOSSIERS_LIRE'] },
  {
    libelle: 'Utilisateurs',
    chemin: '/agent/utilisateurs',
    droits: [UTILISATEURS_LIRE, UTILISATEURS_GERER],
  },
  { libelle: 'Rôles', chemin: '/agent/roles', droits: [ROLES_LIRE, ROLES_GERER] },
];
const PIED: LienPied[] = [];
const PUBLICS: LienVitrine[] = [
  { libelle: 'La démarche', ancre: '#demarche' },
  { libelle: 'Ce qui est vérifié', ancre: '#verifie' },
  { libelle: 'Comment ça se passe', ancre: '#comment' },
];

@Component({
  selector: 'ps-racine',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterOutlet, BarreVitrine, Entete, Pied],
  template: `@if (vitrine()) {
      <sira-barre-vitrine [service]="configuration.nom" [liens]="publics">
        <a vitrine-action class="sv-bouton sv-bouton--clair" routerLink="/commencer">Commencer</a>
      </sira-barre-vitrine>
      <main id="contenu" class="sira-vitrine-page" tabindex="-1"><router-outlet /></main>
      <footer class="sv-pied">
        <div class="sira-vitrine__largeur sv-pied__contenu">
          <img src="assets/sira/marque/sira-logo-clair.png" alt="Sira" width="110" height="37" />
          <p>{{ configuration.organisme }}</p>
        </div>
      </footer>
    } @else {
      <sira-entete
        signe="landmark"
        [nom]="configuration.nom"
        [service]="configuration.organisme"
        [liens]="liens()"
        [utilisateur]="session.utilisateur()?.nom"
        [connecte]="session.connecte()"
        (deconnexion)="session.seDeconnecter()"
      />
      <main id="contenu" class="sira-page sira-conteneur" tabindex="-1"><router-outlet /></main>
      <sira-pied [liens]="pied">{{ configuration.organisme }}</sira-pied>
    }`,
  host: { class: 'sira-application', '[class.sira-application--vitrine]': 'vitrine()' },
})
export class App {
  protected readonly configuration = inject(CONFIGURATION);
  protected readonly session = inject(Session);
  protected readonly pied = PIED;
  protected readonly publics = PUBLICS;
  private readonly routeur = inject(Router);
  private readonly adresse = toSignal(
    this.routeur.events.pipe(
      filter((evenement) => evenement instanceof NavigationEnd),
      map(() => this.routeur.url),
    ),
    { initialValue: this.routeur.url },
  );
  /** Page d'accueil d'un visiteur : barre publique, sans le menu des agents. */
  protected readonly vitrine = computed(
    () => !this.session.connecte() && this.adresse().split(/[?#]/)[0] === '/',
  );
  /** Seul un agent a une session : le citoyen ne voit aucun menu. */
  protected readonly liens = computed(() =>
    this.session.connecte() ? MENU_AGENT.filter((lien) => this.session.peut(...lien.droits)) : [],
  );

  constructor() {
    // L'accueil est public : un serveur injoignable ne doit pas empêcher de l'afficher.
    this.session.charger().catch(() => undefined);
  }
}
