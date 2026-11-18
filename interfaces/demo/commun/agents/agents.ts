import { Injectable, inject } from '@angular/core';
import { Api, Page } from '@sira/coquille';
import { Observable } from 'rxjs';

export * from './droits';

/** Un agent de l'administration (compte de l'annuaire et rôle dans l'application). */
export interface Utilisateur {
  id: string;
  identifiant: string;
  prenom: string;
  nom: string;
  email: string;
  actif: boolean;
  /** Le mot de passe provisoire n'a pas encore été changé. */
  premiere_connexion: boolean;
  cree_le: string | null;
  role: string | null;
  libelle_role: string | null;
}

export interface SaisieUtilisateur {
  prenom: string;
  nom: string;
  email: string;
  role: string;
  actif?: boolean;
}

export interface Permission {
  code: string;
  libelle: string;
  groupe: string;
}

export interface Role {
  /** Identifiant technique (UUID), celui des adresses. */
  id: string;
  code: string;
  libelle: string;
  description: string;
  permissions: string[];
  /** Fourni avec l'application : il ne se supprime pas. */
  systeme: boolean;
  utilisateurs: number;
}

export interface SaisieRole {
  libelle: string;
  description: string;
  permissions: string[];
}

/** Les agents et leurs rôles, sur le serveur de l'application (registre ou service). */
@Injectable({ providedIn: 'root' })
export class Agents {
  private readonly api = inject(Api);

  utilisateurs(page: number, taille: number, recherche: string): Observable<Page<Utilisateur>> {
    return this.api.lire('/api/utilisateurs', { page, taille, recherche });
  }

  creerUtilisateur(
    saisie: SaisieUtilisateur,
  ): Observable<{ utilisateur: Utilisateur; courriel_envoye: boolean }> {
    return this.api.creer('/api/utilisateurs', saisie);
  }

  modifierUtilisateur(id: string, saisie: SaisieUtilisateur): Observable<Utilisateur> {
    return this.api.remplacer(`/api/utilisateurs/${encodeURIComponent(id)}`, saisie);
  }

  reinitialiser(id: string): Observable<{ courriel_envoye: boolean }> {
    return this.api.creer(`/api/utilisateurs/${encodeURIComponent(id)}/reinitialisation`);
  }

  supprimerUtilisateur(id: string): Observable<void> {
    return this.api.supprimer(`/api/utilisateurs/${encodeURIComponent(id)}`);
  }

  roles(): Observable<Role[]> {
    return this.api.lire('/api/roles');
  }

  permissions(): Observable<Permission[]> {
    return this.api.lire('/api/permissions');
  }

  creerRole(saisie: SaisieRole): Observable<Role> {
    return this.api.creer('/api/roles', saisie);
  }

  modifierRole(id: string, saisie: SaisieRole): Observable<Role> {
    return this.api.remplacer(`/api/roles/${encodeURIComponent(id)}`, saisie);
  }

  supprimerRole(id: string): Observable<void> {
    return this.api.supprimer(`/api/roles/${encodeURIComponent(id)}`);
  }
}
