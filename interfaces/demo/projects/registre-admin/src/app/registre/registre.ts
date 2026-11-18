import { Injectable, inject } from '@angular/core';
import { Api } from '@sira/coquille';
import { formaterJour } from '@sira/ui';
import { Observable, shareReplay } from 'rxjs';

/** Un champ de la fiche d'une personne, tel que le registre le décrit. */
export interface ChampDeFiche {
  nom: string;
  libelle: string;
  type: 'TEXTE' | 'DATE' | 'NOMBRE' | 'BOOLEEN' | 'CHOIX';
  /** L'agent peut corriger ce champ. */
  modifiable: boolean;
  facultatif: boolean;
  minimum: number;
  maximum: number;
  /** Valeurs admises d'un `CHOIX`, avec leur libellé. */
  choix: Record<string, string>;
}

/** Un document que le registre délivre, et les informations qu'il imprime. */
export interface DocumentDuRegistre {
  type: string;
  titre: string;
  champs: string[];
}

export interface DescriptionRegistre {
  code: string;
  nom: string;
  organisme: string;
  champs: ChampDeFiche[];
  documents: DocumentDuRegistre[];
}

/** Le même écran sert les quatre registres : c'est le serveur qui dit quels champs afficher. */
@Injectable({ providedIn: 'root' })
export class Registre {
  /** La description ne change pas tant que le serveur tourne : elle est lue une seule fois. */
  readonly description: Observable<DescriptionRegistre> = inject(Api)
    .lire<DescriptionRegistre>('/api/registre')
    .pipe(shareReplay({ bufferSize: 1, refCount: false }));
}

/** Valeur d'un champ écrite pour être lue : « Oui », « 12 mars 1998 », « 12,5 ». */
export function afficher(champ: ChampDeFiche, valeur: unknown): string {
  if (valeur === null || valeur === undefined || valeur === '') {
    return '—';
  }
  switch (champ.type) {
    case 'BOOLEEN':
      return valeur === true ? 'Oui' : 'Non';
    case 'DATE':
      // Midi : le jour affiché reste le même quel que soit le fuseau du navigateur.
      return formaterJour(`${String(valeur)}T12:00:00`);
    case 'CHOIX':
      return champ.choix[String(valeur)] ?? String(valeur);
    case 'NOMBRE':
      return String(valeur).replace('.', ',');
    default:
      return String(valeur);
  }
}
