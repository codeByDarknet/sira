import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Session } from '@sira/coquille';
import { BarreHaute, Chargement, Entete, LienNavigation } from '@sira/ui';

const MENU: LienNavigation[] = [
  { libelle: 'Tableau de bord', chemin: '/', exact: true, icone: 'layout-dashboard' },
  { libelle: 'Membres', chemin: '/membres', icone: 'users' },
  { libelle: 'Registres', chemin: '/registres', icone: 'building-2' },
  { libelle: 'Questions', chemin: '/questions', icone: 'message-square' },
  { libelle: 'Démarches', chemin: '/demarches', icone: 'list' },
  { libelle: 'Habilitations', chemin: '/habilitations', icone: 'shield-check' },
  { libelle: 'Journal', chemin: '/journal', icone: 'history' },
  { libelle: 'Signalements', chemin: '/signalements', icone: 'flag' },
];

@Component({
  selector: 'co-racine',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, BarreHaute, Entete, Chargement],
  template: `@if (session.prete()) {
      <sira-entete
        laterale
        service="Console d’administration"
        [liens]="liens()"
        [utilisateur]="session.utilisateur()?.nom"
        [connecte]="session.connecte()"
        (deconnexion)="session.seDeconnecter()"
      />
      @if (session.connecte()) {
        <sira-barre-haute
          service="Console d’administration"
          [utilisateur]="session.utilisateur()?.nom"
          (deconnexion)="session.seDeconnecter()"
        />
      }
      <main id="contenu" class="sira-page sira-conteneur sira-conteneur--large" tabindex="-1">
        <router-outlet />
      </main>
    } @else {
      <sira-chargement />
    }`,
  host: { class: 'sira-application sira-application--laterale' },
})
export class App {
  protected readonly session = inject(Session);
  protected readonly liens = computed(() => (this.session.connecte() ? MENU : []));

  constructor() {
    // Les gardes lisent aussi la session ; une page sans garde (erreur) ne doit pas rester en chargement.
    this.session.charger().catch(() => undefined);
  }
}
