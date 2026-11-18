import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Api } from '../api/api';
import { CHEMINS_API, adresseConnexion } from '../api/chemins';
import { SANS_REDIRECTION } from '../api/intercepteur';
import { CHEMIN_COURANT, REDIRECTION } from '../api/redirection';

/** Réponse de `GET /api/session`. */
export interface Utilisateur {
  nom: string;
  /** Identifiant de connexion (nom d'utilisateur, ou identifiant unique du citoyen). */
  identifiant?: string | null;
  courriel?: string | null;
  roles: string[];
  /** Droits de l'agent (registres et services), donnés par son rôle ; vide ailleurs. */
  permissions?: string[];
}

/**
 * Session de l'usager. L'interface ne détient aucun jeton : elle demande au serveur qui est
 * connecté, et le serveur reste seul juge de chaque appel.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  private readonly api = inject(Api);
  private readonly rediriger = inject(REDIRECTION);
  private readonly cheminCourant = inject(CHEMIN_COURANT);

  private readonly etat = signal<Utilisateur | null>(null);
  private readonly lue = signal(false);
  private readonly enRedirection = signal(false);
  private lecture?: Promise<Utilisateur | null>;

  readonly utilisateur = this.etat.asReadonly();
  readonly connecte = computed(() => this.etat() !== null);
  /**
   * Vrai une fois la session lue, sauf pendant le départ vers la page de connexion : l'application
   * n'affiche son cadre (menu, compte) qu'à ce moment, et un écran de chargement avant.
   */
  readonly prete = computed(() => this.lue() && !this.enRedirection());

  /** Lit la session une seule fois ; les appels suivants reprennent la même réponse. */
  charger(): Promise<Utilisateur | null> {
    this.lecture ??= this.lire();
    return this.lecture;
  }

  aLeRole(role: string): boolean {
    return this.etat()?.roles.includes(role) ?? false;
  }

  /**
   * Vrai si l'agent a au moins un des droits cités. Sert à montrer ou cacher un menu, une page ou
   * un bouton ; le serveur, lui, vérifie chaque appel.
   */
  peut(...permissions: string[]): boolean {
    const accordees = this.etat()?.permissions ?? [];
    return permissions.some((permission) => accordees.includes(permission));
  }

  seConnecter(retour = this.cheminCourant()): void {
    this.enRedirection.set(true);
    this.rediriger(adresseConnexion(retour));
  }

  /**
   * Change le mot de passe ou l'application d'authentification chez le fournisseur d'identité
   * (action lancée par l'application), puis revient sur la page courante.
   */
  agirSurLeCompte(
    action: 'mot-de-passe' | 'authentification',
    retour = this.cheminCourant(),
  ): void {
    this.enRedirection.set(true);
    this.rediriger(`${adresseConnexion(retour)}&action=${action}`);
  }

  async seDeconnecter(): Promise<void> {
    const reponse = await firstValueFrom(
      this.api.creer<{ url?: string } | null>(CHEMINS_API.deconnexion),
    );
    this.etat.set(null);
    this.enRedirection.set(true);
    this.rediriger(reponse?.url ?? '/');
  }

  private async lire(): Promise<Utilisateur | null> {
    const contexte = new HttpContext().set(SANS_REDIRECTION, true);
    try {
      const utilisateur = await firstValueFrom(
        this.api.lire<Utilisateur>(CHEMINS_API.session, {}, contexte),
      );
      this.etat.set(utilisateur);
      return utilisateur;
    } catch (erreur) {
      if (erreur instanceof HttpErrorResponse && erreur.status === 401) {
        return null;
      }
      // Serveur injoignable : la prochaine navigation retentera la lecture.
      this.lecture = undefined;
      throw erreur;
    } finally {
      this.lue.set(true);
    }
  }
}
