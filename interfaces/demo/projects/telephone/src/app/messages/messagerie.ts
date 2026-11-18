import { Injectable, inject } from '@angular/core';
import { Api } from '@sira/coquille';
import { Observable, map } from 'rxjs';

export interface Message {
  id: string;
  recu_le: string;
  texte: string;
  /** Code à usage unique lu dans le texte, quand le message en porte un. */
  code: string | null;
}

/** Ce que la messagerie de démonstration (Mailpit) renvoie pour une recherche. */
interface Recherche {
  messages: { ID: string; Created: string; Snippet: string }[];
}

/** Seul chemin relayé vers la messagerie, en lecture : la recherche de messages. */
const CHEMIN = '/messagerie/search';
const NOMBRE_AFFICHE = 10;
const CODE = /\b\d{6}\b/;

@Injectable({ providedIn: 'root' })
export class Messagerie {
  private readonly api = inject(Api);

  /** Derniers messages reçus à cette adresse, le plus récent d'abord. */
  recus(adresse: string): Observable<Message[]> {
    return this.api
      .lire<Recherche>(CHEMIN, { query: `to:"${adresse}"`, limit: NOMBRE_AFFICHE })
      .pipe(
        map((recherche) =>
          recherche.messages.map((message) => ({
            id: message.ID,
            recu_le: message.Created,
            texte: message.Snippet,
            code: CODE.exec(message.Snippet)?.[0] ?? null,
          })),
        ),
      );
  }
}
