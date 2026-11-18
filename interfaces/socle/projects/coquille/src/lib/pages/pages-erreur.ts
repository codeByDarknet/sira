import { ChangeDetectionStrategy, Component, DOCUMENT, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Bouton, Lien } from '@sira/ui';

/** Adresse inconnue. */
@Component({
  selector: 'sira-page-introuvable',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Lien],
  template: `<h1>Page introuvable</h1>
    <p><a siraLien icone="arrow-left" routerLink="/">Revenir à l’accueil</a></p>`,
})
export class PageIntrouvable {}

/** Usager connecté, mais sans le rôle attendu. */
@Component({
  selector: 'sira-page-acces-refuse',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Lien],
  template: `<h1>Accès refusé</h1>
    <p class="sira-chapeau">En cas d’erreur, contactez le gestionnaire des comptes.</p>
    <p><a siraLien icone="arrow-left" routerLink="/">Revenir à l’accueil</a></p>`,
})
export class PageAccesRefuse {}

/** Serveur injoignable au moment d'ouvrir un écran. */
@Component({
  selector: 'sira-page-indisponible',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Bouton],
  template: `<h1>Service indisponible</h1>
    <p class="sira-chapeau">Rien n’a été perdu. Réessayez dans quelques minutes.</p>
    <p><button siraBouton type="button" (click)="recharger()">Réessayer</button></p>`,
})
export class PageIndisponible {
  private readonly document = inject(DOCUMENT);

  protected recharger(): void {
    this.document.location.assign('/');
  }
}
