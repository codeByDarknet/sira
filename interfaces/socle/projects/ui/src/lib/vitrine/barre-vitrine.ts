import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Lien du menu public : une page de l'application ou une ancre de la page d'accueil. */
export interface LienVitrine {
  libelle: string;
  /** Route de l'application (`/aide`) ; sans elle, `ancre` (`#comment`). */
  chemin?: string;
  ancre?: string;
}

/**
 * Barre des pages publiques (visiteur non connecté) : logo à gauche, liens groupés dans une
 * pilule au centre, action principale à droite (`<button vitrine-action>`). Fond foncé, posée
 * au-dessus du bandeau d'accueil de la même couleur.
 */
@Component({
  selector: 'sira-barre-vitrine',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive],
  template: `<div class="sira-vitrine__largeur sira-barre-vitrine__contenu">
    <a class="sira-barre-vitrine__marque" routerLink="/" [attr.aria-label]="nom() + ', accueil'">
      <img src="assets/sira/marque/sira-logo-clair.png" alt="" width="132" height="44" />
      @if (service()) {
        <span class="sira-barre-vitrine__service">{{ service() }}</span>
      }
    </a>
    @if (liens().length > 0) {
      <nav class="sira-barre-vitrine__liens" aria-label="Menu">
        @for (lien of liens(); track lien.libelle) {
          @if (lien.chemin) {
            <a
              [routerLink]="lien.chemin"
              routerLinkActive="sira-barre-vitrine__lien--actif"
              [routerLinkActiveOptions]="{ exact: true }"
              class="sira-barre-vitrine__lien"
              >{{ lien.libelle }}</a
            >
          } @else {
            <a [href]="lien.ancre" class="sira-barre-vitrine__lien">{{ lien.libelle }}</a>
          }
        }
      </nav>
    }
    <div class="sira-barre-vitrine__action"><ng-content select="[vitrine-action]" /></div>
  </div>`,
  host: { class: 'sira-barre-vitrine', role: 'banner' },
})
export class BarreVitrine {
  readonly nom = input('Sira');
  readonly service = input<string>();
  readonly liens = input<readonly LienVitrine[]>([]);
}
