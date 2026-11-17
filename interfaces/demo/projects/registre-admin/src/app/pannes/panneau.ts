import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Session, lireProbleme } from '@sira/coquille';
import {
  Alerte,
  Bouton,
  Champ,
  DatePipe,
  Dialogue,
  Etiquette,
  FiltreFrappe,
  Icone,
  Saisie,
  Squelette,
} from '@sira/ui';
import { LIBELLES_MODE, MODES, Mode, Pannes } from './panne';

interface Effet {
  /** Ce que fait le registre. */
  registre: string;
  /** Ce que Sira fait alors, et ce que voit le citoyen. */
  sira: string;
}

/** Chaque état, dit simplement : ce qui se passe côté registre, puis côté Sira. */
const EFFETS: Record<Mode, Effet> = {
  NORMAL: {
    registre: 'Le registre répond tout de suite, comme en temps ordinaire.',
    sira: 'Sira reçoit les informations sans attendre et les transmet au service.',
  },
  LENT: {
    registre: 'Le registre met plusieurs secondes à répondre, comme un serveur surchargé.',
    sira: 'Au-delà de 2 secondes, Sira cesse d’attendre, garde la demande et réessaie plus tard.',
  },
  ERREURS: {
    registre: 'Le registre répond toujours par une erreur, comme un serveur en panne.',
    sira: 'Sira ne transmet rien de faux : la demande reste en attente et repart plus tard.',
  },
  MUET: {
    registre: 'Le registre ne répond plus du tout, comme une coupure du réseau.',
    sira: 'Sira attend un peu, abandonne, puis réessaie : le citoyen est prévenu du délai.',
  },
};

const DELAI_MIN_MS = 100;
const DELAI_MAX_MS = 30_000;

/** Page des pannes : l'agent choisit comment le registre répond à Sira, pour la démonstration. */
@Component({
  selector: 'ra-panneau',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    Alerte,
    Bouton,
    Champ,
    DatePipe,
    Dialogue,
    Etiquette,
    FiltreFrappe,
    Icone,
    Saisie,
    Squelette,
  ],
  templateUrl: './panneau.html',
})
export class Panneau {
  /** Ce que le rôle de l'agent lui permet : boutons montrés ou cachés. */
  protected readonly session = inject(Session);
  private readonly pannes = inject(Pannes);

  protected readonly modes = MODES;
  protected readonly libelles = LIBELLES_MODE;
  protected readonly effets = EFFETS;

  protected readonly etat = rxResource({ stream: () => this.pannes.lire() });
  protected readonly probleme = computed(() =>
    this.etat.error() ? lireProbleme(this.etat.error()).message : null,
  );

  /** Mode en cours d'envoi : son bouton attend, les autres restent utilisables. */
  protected readonly envoi = signal<Mode | null>(null);
  protected readonly echec = signal<string | null>(null);

  /** Fenêtre de l'état « Lent » : l'attente se saisit au moment d'activer. */
  protected readonly fenetreDelai = signal(false);
  /** État à confirmer avant de l'activer (tous sauf « Lent », qui a sa propre fenêtre). */
  protected readonly aConfirmer = signal<Mode | null>(null);
  protected delai = '5';
  protected readonly erreurDelai = signal<string | null>(null);

  protected enSecondes(delaiMs: number): string {
    return String(delaiMs / 1000).replace('.', ',');
  }

  protected activer(mode: Mode): void {
    if (mode === 'LENT') {
      const actuel = this.etat.value();
      this.delai = actuel ? this.enSecondes(actuel.delai_ms) : '5';
      this.erreurDelai.set(null);
      this.fenetreDelai.set(true);
      return;
    }
    this.echec.set(null);
    this.aConfirmer.set(mode);
  }

  protected confirmerMode(): void {
    const mode = this.aConfirmer();
    if (mode) {
      this.envoyer(mode);
    }
  }

  protected confirmerDelai(): void {
    const delaiMs = Math.round(Number(this.delai.trim().replace(',', '.')) * 1000);
    if (!(delaiMs >= DELAI_MIN_MS && delaiMs <= DELAI_MAX_MS)) {
      this.erreurDelai.set('Saisissez un nombre de secondes entre 0,1 et 30. Exemple : 5');
      setTimeout(() => document.getElementById('panne-delai')?.focus());
      return;
    }
    this.envoyer('LENT', delaiMs);
  }

  private envoyer(mode: Mode, delaiMs?: number): void {
    this.echec.set(null);
    this.envoi.set(mode);
    this.pannes.changer(mode, delaiMs).subscribe({
      next: (panne) => {
        this.envoi.set(null);
        this.fenetreDelai.set(false);
        this.aConfirmer.set(null);
        this.etat.set(panne);
      },
      error: (erreur: unknown) => {
        this.envoi.set(null);
        const message = lireProbleme(erreur).message;
        if (this.fenetreDelai()) {
          this.erreurDelai.set(message);
        } else {
          this.echec.set(message);
        }
      },
    });
  }
}
