import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  inject,
  input,
} from '@angular/core';
import { Bouton, Icone } from '@sira/ui';
import { Session } from '../session/session';

/**
 * Page « Mon profil » : qui est connecté, avec quels rôles, et ce que la personne peut changer
 * elle-même. Le mot de passe et l'application d'authentification se changent sur les pages du
 * fournisseur d'identité (Keycloak), puis la personne revient ici.
 *
 * Route : `{ path: 'profil', loadComponent: …, data: { actionsCompte: true, roles: {…} } }`.
 */
@Component({
  selector: 'sira-page-profil',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Bouton, Icone],
  templateUrl: './page-profil.html',
})
export class PageProfil {
  protected readonly session = inject(Session);

  /** Le compte a un mot de passe et une application d'authentification (console). */
  readonly actionsCompte = input(false, { transform: booleanAttribute });
  /** Libellé lisible de chaque rôle technique. */
  readonly roles = input<Record<string, string>>({});

  protected readonly lesRoles = computed(() =>
    (this.session.utilisateur()?.roles ?? [])
      .map((role) => this.roles()[role])
      .filter((libelle): libelle is string => Boolean(libelle)),
  );

  protected readonly initiales = computed(() => {
    const mots = (this.session.utilisateur()?.nom ?? '').split(/\s+/).filter(Boolean);
    const lettres = mots.length > 1 ? [mots[0], mots[mots.length - 1]] : mots;
    return lettres.map((mot) => mot.charAt(0).toLocaleUpperCase('fr')).join('') || '·';
  });
}
