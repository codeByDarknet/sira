import { DOCUMENT, Injectable, InjectionToken, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

/** Nom de l'application, repris à la fin du titre de chaque page. */
export const NOM_APPLICATION = new InjectionToken<() => string>('NOM_APPLICATION');

/**
 * Titre de page « Écran — Application », puis focus sur le titre principal : après un
 * changement d'écran, le lecteur d'écran annonce où l'on est arrivé.
 */
@Injectable()
export class TitreDePage extends TitleStrategy {
  private readonly titre = inject(Title);
  private readonly application = inject(NOM_APPLICATION);
  private readonly document = inject(DOCUMENT);
  private premierAffichage = true;

  override updateTitle(etat: RouterStateSnapshot): void {
    const ecran = this.buildTitle(etat);
    const application = this.application();
    this.titre.setTitle(ecran ? `${ecran} — ${application}` : application);

    if (this.premierAffichage) {
      this.premierAffichage = false;
      return;
    }
    // Le nouvel écran n'est dans la page qu'après le rendu qui suit la navigation.
    setTimeout(() => {
      const cible =
        this.document.querySelector<HTMLElement>('main h1') ?? this.document.querySelector('main');
      cible?.setAttribute('tabindex', '-1');
      cible?.focus();
    });
  }
}
