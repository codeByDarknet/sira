import { Injectable, inject } from '@angular/core';
import { Api } from '@sira/coquille';
import { Observable } from 'rxjs';

/** Les modèles reprennent les noms du JSON du serveur, sans conversion. */
export type Canal = 'REDIRECTION' | 'CODE' | 'NOTIFICATION';
export type EtatDemande =
  'EN_ATTENTE_ACCORD' | 'REFUSEE' | 'EXPIREE' | 'ANNULEE' | 'EN_COURS' | 'PARTIELLE' | 'COMPLETE';
export type Niveau = 'STATUT' | 'CHAMPS' | 'DOCUMENT';
export type EtatVerification =
  'A_TRAITER' | 'OBTENU' | 'REUTILISE' | 'REFUSE' | 'INCONNU' | 'EXPIRE';
export type EtatDossier = 'EN_VERIFICATION' | 'COMPLET' | 'A_EXAMINER';

export type NatureDeclaration = 'TEXTE' | 'PIECE';

export interface Jeu {
  code: string;
  libelle: string;
  niveau: Niveau;
  registre: string;
  /** Champs attendus d'un jeu de niveau CHAMPS ; vide pour les autres. */
  champs: string[];
}

/** Ce que la personne renseigne elle-même : le reste vient des registres. */
export interface DeclarationAttendue {
  code: string;
  libelle: string;
  aide: string | null;
  nature: NatureDeclaration;
}

export interface PieceRecue {
  nom: string;
  type: string;
  taille: number;
}

export interface Demarche {
  service: { code: string; nom: string; organisme: string };
  code: string;
  libelle: string;
  jeux: Jeu[];
  declarations: DeclarationAttendue[];
  /** Pièces que le citoyen aurait dû fournir sans Sira. */
  pieces: string[];
}

export interface Demande {
  id: string;
  canal: Canal;
  etat: EtatDemande;
  url_accord: string | null;
  code_court: string | null;
  code_verification: string | null;
  expire_le: string;
  /** Vrai dès que l'accord est donné : le dossier peut être affiché. */
  dossier_ouvert: boolean;
}

export interface Verification {
  jeu: string;
  libelle: string;
  niveau: Niveau;
  registre: string;
  etat: EtatVerification;
  valeur: boolean | null;
  champs: Record<string, string | number | boolean | null> | null;
  piece: PieceRecue | null;
  modifiee_le: string;
}

/** Une information déclarée par la personne, jamais vérifiée par un registre. */
export interface Declaration {
  code: string;
  libelle: string;
  nature: NatureDeclaration;
  /** Vrai quand la pièce remplace une information qu'un registre n'a pas transmise. */
  remplace: boolean;
  valeur: string | null;
  piece: PieceRecue | null;
  modifiee_le: string;
}

export interface Dossier {
  id: string;
  numero: string;
  etat: EtatDossier;
  cree_le: string;
  modifie_le: string;
  /** Absente tant que la personne n'a pas déposé son dossier. */
  depose_le: string | null;
  verifications: Verification[];
  declarations: Declaration[];
  pieces_evitees: string[];
}

const CHEMIN = '/api/parcours';

/** Le parcours du citoyen : le serveur ne connaît de lui que la demande ouverte par ce navigateur. */
@Injectable({ providedIn: 'root' })
export class Parcours {
  private readonly api = inject(Api);

  demarche(): Observable<Demarche> {
    return this.api.lire('/api/demarche');
  }

  /** L'identifiant unique part vers Sira, qui vérifie qu'il est celui de la personne. */
  ouvrir(canal: Canal, identifiantUnique: string): Observable<Demande> {
    return this.api.creer(`${CHEMIN}/demandes`, { canal, identifiant_unique: identifiantUnique });
  }

  suivre(): Observable<Demande> {
    return this.api.lire(`${CHEMIN}/demande`);
  }

  revenir(demande: string, codeRetour: string): Observable<Demande> {
    return this.api.creer(`${CHEMIN}/retour`, { demande, code_retour: codeRetour });
  }

  abandonner(): Observable<void> {
    return this.api.supprimer(`${CHEMIN}/demande`);
  }

  dossier(): Observable<Dossier> {
    return this.api.lire(`${CHEMIN}/dossier`);
  }

  /** Une saisie libre du formulaire du service. */
  saisir(code: string, valeur: string): Observable<Dossier> {
    return this.api.remplacer(declaration(code), { valeur });
  }

  /** Une pièce PDF, envoyée telle quelle : son nom ne sert qu'à l'affichage. */
  joindre(code: string, fichier: File): Observable<Dossier> {
    const corps = new Blob([fichier], { type: 'application/pdf' });
    const nom = encodeURIComponent(fichier.name.slice(0, 255));
    return this.api.remplacer(`${declaration(code)}/piece?nom=${nom}`, corps);
  }

  retirer(code: string): Observable<Dossier> {
    return this.api.supprimer(declaration(code));
  }

  /** Ce que la personne a déclaré est figé ; les réponses attendues continuent d'arriver. */
  deposer(): Observable<Dossier> {
    return this.api.creer(`${CHEMIN}/dossier/depot`);
  }

  terminer(): Observable<void> {
    return this.api.supprimer(CHEMIN);
  }
}

function declaration(code: string): string {
  return `${CHEMIN}/dossier/declarations/${encodeURIComponent(code)}`;
}

/** Adresses d'une pièce du dossier en cours : téléchargement, ou aperçu dans un nouvel onglet. */
export interface AdressesPiece {
  telechargement: string;
  apercu: string;
}

export function adresses(chemin: string): AdressesPiece {
  return { telechargement: chemin, apercu: `${chemin}?apercu=true` };
}

/** Pièce reçue d'un registre pour une condition du dossier en cours. */
export function adressePiece(jeu: string): AdressesPiece {
  return adresses(`${CHEMIN}/dossier/pieces/${encodeURIComponent(jeu)}`);
}

/** Pièce jointe par la personne. */
export function adressePieceDeclaree(code: string): AdressesPiece {
  return adresses(`${declaration(code)}/piece`);
}
