import { Injectable } from '@angular/core';

/**
 * Message laissé par un écran pour le suivant (« Le membre a été enregistré »). Il ne passe
 * pas par l'adresse : un lien forgé ne peut pas faire afficher un faux message.
 */
@Injectable({ providedIn: 'root' })
export class Annonces {
  private message: string | null = null;

  laisser(message: string): void {
    this.message = message;
  }

  /** Rend le message une seule fois. */
  prendre(): string | null {
    const message = this.message;
    this.message = null;
    return message;
  }
}
