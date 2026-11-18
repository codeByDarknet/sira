import { computed, signal } from '@angular/core';
import { lireProbleme } from '@sira/coquille';
import { ErreurDeChamp } from '@sira/ui';
import { Observable } from 'rxjs';

/** Erreurs par champ ; la clé est le nom du champ dans le JSON du serveur. */
export type Erreurs = Record<string, string>;

/**
 * Suivi d'un envoi au serveur : erreurs par champ (locales, puis celles du serveur),
 * message d'échec et blocage du double envoi.
 */
export class Envoi {
  readonly erreurs = signal<Erreurs>({});
  readonly resume = computed<ErreurDeChamp[]>(() =>
    Object.entries(this.erreurs()).map(([champ, message]) => ({ champ, message })),
  );
  readonly echec = signal<string | null>(null);
  readonly enCours = signal(false);

  /** N'appelle le serveur que si la vérification locale n'a rien trouvé. */
  lancer<T>(erreurs: Erreurs, appel: () => Observable<T>, suite: (reponse: T) => void): void {
    this.erreurs.set(erreurs);
    this.echec.set(null);
    if (Object.keys(erreurs).length > 0) {
      return;
    }
    this.enCours.set(true);
    appel().subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        suite(reponse);
      },
      error: (erreur: unknown) => {
        const probleme = lireProbleme(erreur);
        this.enCours.set(false);
        this.erreurs.set(probleme.champs);
        if (Object.keys(probleme.champs).length === 0) {
          this.echec.set(probleme.message);
        }
      },
    });
  }
}
