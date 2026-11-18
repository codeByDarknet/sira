import { Injectable, inject } from '@angular/core';
import { Api, Page } from '@sira/coquille';
import { Observable } from 'rxjs';

/** Les modèles reprennent les noms du JSON du serveur, sans conversion. */
export interface ResumePersonne {
  /** Identifiant technique (UUID), celui des adresses. */
  id: string;
  /** Identifiant unique de la personne (douze chiffres). */
  identifiant: string;
  nom: string;
  prenoms: string;
  date_naissance: string;
  modifiee_le: string;
}

export type Fiche = Record<string, string | number | boolean | null>;

export interface Personne {
  id: string;
  identifiant: string;
  fiche: Fiche;
  modifiee_le: string;
}

/** Information saisie par l'agent sur un « autre document » : libellé, type, valeur. */
export interface InformationLibre {
  libelle: string;
  type: 'TEXTE' | 'DATE' | 'NOMBRE' | 'BOOLEEN';
  valeur: string;
}

/** Une pièce déposée par un agent : fichier, type et informations qu'il contient. */
export interface Piece {
  id: string;
  type: string;
  titre: string;
  champs: string[];
  informations?: InformationLibre[];
  nom_fichier: string;
  type_mime: string;
  taille: number;
  depose_par: string;
  depose_le: string;
}

/** Ce que l'agent choisit pour déposer une pièce. */
export interface DepotPiece {
  type: string;
  titre: string;
  champs: string[];
  informations: InformationLibre[];
  fichier: File;
}

const CHEMIN = '/api/personnes';

@Injectable({ providedIn: 'root' })
export class Personnes {
  private readonly api = inject(Api);

  lister(page: number, recherche: string): Observable<Page<ResumePersonne>> {
    return this.api.lire(CHEMIN, { page, taille: 10, recherche });
  }

  lire(identifiant: string): Observable<Personne> {
    return this.api.lire(adresse(identifiant));
  }

  /** Inscrit une nouvelle personne ; sans identifiant, le registre en attribue un. */
  creer(champs: Fiche, identifiant?: string): Observable<Personne> {
    return this.api.creer(CHEMIN, { champs, ...(identifiant ? { identifiant } : {}) });
  }

  /** Retire la personne du registre. */
  supprimer(identifiant: string): Observable<void> {
    return this.api.supprimer(adresse(identifiant));
  }

  pieces(id: string): Observable<Piece[]> {
    return this.api.lire(`${adresse(id)}/pieces`);
  }

  deposer(id: string, depot: DepotPiece): Observable<Piece[]> {
    const corps = new FormData();
    corps.append('fichier', depot.fichier);
    corps.append('titre', depot.titre);
    if (depot.type) {
      corps.append('type', depot.type);
    }
    for (const champ of depot.champs) {
      corps.append('champs', champ);
    }
    if (depot.informations.length > 0) {
      corps.append('informations', JSON.stringify(depot.informations));
    }
    return this.api.creer(`${adresse(id)}/pieces`, corps);
  }

  retirerPiece(id: string, piece: string): Observable<void> {
    return this.api.supprimer(`${adresse(id)}/pieces/${encodeURIComponent(piece)}`);
  }

  /** Corrige quelques champs (modification partielle). */
  corriger(identifiant: string, champs: Fiche): Observable<Personne> {
    return this.api.modifier(adresse(identifiant), { champs });
  }
}

function adresse(identifiant: string): string {
  return `${CHEMIN}/${encodeURIComponent(identifiant)}`;
}

/** Adresse du document PDF d'une personne, ouverte par un lien. */
export function adresseDocument(identifiant: string, type: string): string {
  return `${adresse(identifiant)}/documents/${encodeURIComponent(type)}`;
}

/** Adresse du fichier d'une pièce déposée. */
export function adressePiece(id: string, piece: string): string {
  return `${adresse(id)}/pieces/${encodeURIComponent(piece)}`;
}
