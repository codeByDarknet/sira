import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Ressource } from '../commun/ressource';
import type { Niveau, Operateur } from '../jeux/jeux';

/** Sira joint toujours le connecteur directement, en TLS mutuel. */
export type Transport = 'DIRECT';

/** Le système d'une administration, joint par son connecteur. */
export interface Registre {
  code: string;
  nom: string;
  /** Code du membre qui tient ce registre. */
  membre: string;
  /** Adresse du connecteur posé devant le registre. */
  adresse: string;
  transport: Transport;
  actif: boolean;
}

/** Nature d'une information du registre : elle décide des conditions proposées. */
export type TypeChamp = 'TEXTE' | 'DATE' | 'NOMBRE' | 'BOOLEEN';

/** Une information que le registre accepte d'exposer, lue sur son connecteur. */
export interface ChampSchema {
  chemin: string;
  libelle: string;
  type: TypeChamp;
  niveau: Niveau;
  /** Conditions admises par le registre ; absent : toutes celles du type. */
  operateurs?: Operateur[] | null;
  /** Valeurs admises par le registre ; absent : toute valeur. */
  valeurs?: (string | number | boolean)[] | null;
}

/** Ce que le registre expose : ses informations et les documents qu'il délivre. */
export interface SchemaRegistre {
  champs: ChampSchema[];
  documents: { code: string; libelle: string }[];
}

@Injectable({ providedIn: 'root' })
export class Registres extends Ressource<Registre> {
  protected readonly chemin = '/api/registres';

  /** Décision de l'autorité : l'instructeur ne change pas l'adresse du connecteur. */
  changerAdresse(code: string, adresse: string): Observable<Registre> {
    return this.api.remplacer(this.adresse(code, '/adresse'), { adresse });
  }

  /** Lu en direct sur le connecteur du registre : seules ces informations sont proposées. */
  schema(code: string): Observable<SchemaRegistre> {
    return this.api.lire(this.adresse(code, '/schema'));
  }
}
