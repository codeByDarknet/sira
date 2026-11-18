import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injectable,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icone } from '../icone/icone';

const CLE_MENU = 'sira.menu-replie';

/** Menu latéral replié (icônes seules) ou déplié ; le choix est gardé sur ce navigateur. */
@Injectable({ providedIn: 'root' })
export class EtatMenu {
  readonly replie = signal(lire());

  basculer(): void {
    this.replie.update((replie) => !replie);
    try {
      localStorage.setItem(CLE_MENU, this.replie() ? '1' : '0');
    } catch {
      // Stockage indisponible : le choix vaut pour cette visite seulement.
    }
  }
}

function lire(): boolean {
  try {
    return localStorage.getItem(CLE_MENU) === '1';
  } catch {
    return false;
  }
}

/**
 * Barre du haut des applications à menu latéral : à gauche, le bouton qui replie le menu ; à
 * droite, le compte (nom et initiales). Un clic sur le compte ouvre un petit menu : « Mon profil »
 * (page du profil) et « Se déconnecter ».
 */
@Component({
  selector: 'sira-barre-haute',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone, RouterLink],
  template: `<button
      type="button"
      class="sira-action sira-barre-haute__menu"
      [attr.aria-label]="menu.replie() ? 'Déplier le menu' : 'Replier le menu'"
      [attr.title]="menu.replie() ? 'Déplier le menu' : 'Replier le menu'"
      [attr.aria-expanded]="!menu.replie()"
      (click)="menu.basculer()"
    >
      <sira-icone nom="menu" />
    </button>
    <div class="sira-barre-haute__gauche"><ng-content /></div>
    <div class="sira-barre-haute__droite">
      <button
        type="button"
        class="sira-barre-haute__compte"
        aria-haspopup="menu"
        [attr.aria-expanded]="ouvert()"
        (click)="ouvert.set(!ouvert())"
      >
        <span class="sira-barre-haute__textes">
          <span class="sira-barre-haute__nom">{{ utilisateur() || 'Mon compte' }}</span>
          @if (service()) {
            <span class="sira-barre-haute__service">{{ service() }}</span>
          }
        </span>
        <span class="sira-barre-haute__avatar" aria-hidden="true">
          @if (utilisateur()) {
            {{ initiales() }}
          } @else {
            <sira-icone nom="user" />
          }
        </span>
        <sira-icone class="sira-barre-haute__fleche" nom="chevron-down" />
      </button>
      @if (ouvert()) {
        <div class="sira-barre-haute__panneau" role="menu">
          <a
            role="menuitem"
            class="sira-barre-haute__choix"
            [routerLink]="lienProfil()"
            (click)="ouvert.set(false)"
          >
            <sira-icone nom="user" />
            Mon profil
          </a>
          <button
            role="menuitem"
            type="button"
            class="sira-barre-haute__choix sira-barre-haute__choix--sortie"
            (click)="ouvert.set(false); deconnexion.emit()"
          >
            <sira-icone nom="log-out" />
            Se déconnecter
          </button>
        </div>
      }
    </div>`,
  host: {
    class: 'sira-barre-haute',
    '(document:click)': 'fermerSiDehors($event)',
    '(document:keydown.escape)': 'ouvert.set(false)',
  },
})
export class BarreHaute {
  /** Nom de la personne connectée. */
  readonly utilisateur = input<string | null>();
  /** Application ou espace : « Console d'administration ». */
  readonly service = input<string>();
  /** Page du profil de l'application. */
  readonly lienProfil = input('/profil');
  readonly deconnexion = output();

  protected readonly menu = inject(EtatMenu);
  protected readonly ouvert = signal(false);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Deux lettres : « Paul Arsène NIKIÉMA » donne « PN ». */
  protected readonly initiales = computed(() => {
    const mots = (this.utilisateur() ?? '').split(/\s+/).filter(Boolean);
    const lettres = mots.length > 1 ? [mots[0], mots[mots.length - 1]] : mots;
    return lettres.map((mot) => mot.charAt(0).toLocaleUpperCase('fr')).join('') || '·';
  });

  protected fermerSiDehors(evenement: Event): void {
    if (this.ouvert() && !this.element.nativeElement.contains(evenement.target as Node)) {
      this.ouvert.set(false);
    }
  }
}
