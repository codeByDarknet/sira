import { NiveauInformation } from '@sira/ui';

/** Formes du JSON de l'API de l'espace citoyen : mêmes noms, sans conversion. */

export interface Reference {
  code: string;
  nom: string;
}

export interface ServiceDemandeur extends Reference {
  organisme?: string;
}

export interface Jeu {
  code: string;
  libelle: string;
  niveau: NiveauInformation;
  registre: Reference;
  /** Précision lisible : les informations transmises. */
  detail?: string;
}

export type Canal = 'REDIRECTION' | 'CODE' | 'NOTIFICATION';

export type EtatDemande =
  'EN_ATTENTE_ACCORD' | 'REFUSEE' | 'EXPIREE' | 'ANNULEE' | 'EN_COURS' | 'PARTIELLE' | 'COMPLETE';

export interface Demande {
  id: string;
  etat: EtatDemande;
  canal: Canal;
  creee_le: string;
  expire_le: string;
  /** L'accord exige le code affiché par le service (demande par code ou par notification). */
  code_verification_exige: boolean;
  service: ServiceDemandeur;
  demarche: {
    code: string;
    libelle: string;
    finalite: string;
    duree_autorisation_jours: number;
  };
  jeux: Jeu[];
}

export type Decision = 'ACCEPTER' | 'REFUSER';

export interface ResultatDecision {
  etat: EtatDemande;
  retour_url: string | null;
}

export type EtatAutorisation = 'ACTIVE' | 'RETIREE' | 'EXPIREE';

export interface Autorisation {
  id: string;
  etat: EtatAutorisation;
  accordee_le: string;
  expire_le: string | null;
  retiree_le: string | null;
  service: ServiceDemandeur;
  demarche: { code: string; libelle: string; finalite: string };
  jeux: Jeu[];
  consultations: number;
  derniere_consultation_le: string | null;
}

export type TypeLigne =
  | 'DEMANDE'
  | 'ACCORD'
  | 'REFUS'
  | 'CONSULTATION'
  | 'ECHEC'
  | 'REUTILISATION'
  | 'RETRAIT'
  | 'SIGNALEMENT';

export type ResultatLigne = 'REUSSI' | 'REFUSE' | 'ECHEC' | 'EN_ATTENTE';

export interface LigneJournal {
  id: string;
  horodatage: string;
  type: TypeLigne;
  resultat: ResultatLigne;
  service: Reference;
  registre: Reference | null;
  jeu: { code: string; libelle: string; niveau: NiveauInformation } | null;
  demarche: { code: string; libelle: string } | null;
  finalite: string | null;
  canal: Canal | null;
  id_echange: string | null;
  signalee: boolean;
}

export interface Resume {
  demandes_en_attente: number;
  autorisations_actives: number;
  consultations_30_jours: number;
}
