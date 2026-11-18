import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { lireProbleme } from '@sira/coquille';
import { Alerte, Bouton, Carte, Code, Etapes, Icone, Minuteur, Squelette } from '@sira/ui';
import { firstValueFrom } from 'rxjs';
import { ETAPES } from '../parcours/etapes';
import { Demande, Parcours } from '../parcours/parcours';
import { periodiquement } from '../parcours/periodique';
import { QrCode } from './qr-code';

const INTERVALLE = 2500;

const DEJA_AUTORISEE = {
  titre: 'Accès déjà donné à ce service',
  texte: 'Le dossier ne s’affiche pas ici. Adressez-vous à un agent du service.',
};

const FINS: Partial<Record<Demande['etat'], { titre: string; texte: string }>> = {
  REFUSEE: {
    titre: 'Vous avez refusé la demande',
    texte: 'Rien n’a été transmis.',
  },
  EXPIREE: {
    titre: 'La demande a expiré',
    texte: 'Rien n’a été transmis.',
  },
  ANNULEE: {
    titre: 'La demande a été annulée',
    texte: 'Rien n’a été transmis.',
  }, // Accès déjà donné à ce service : rien ne prouve que la personne est devant cet écran.
  PARTIELLE: DEJA_AUTORISEE,
  COMPLETE: DEJA_AUTORISEE,
};

/**
 * Attente de l'accord donné sur le téléphone. L'écran interroge le serveur à intervalle
 * régulier et passe tout seul à la récupération des informations dès que l'accord est donné.
 */
@Component({
  selector: 'ps-attente',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Alerte, Bouton, Carte, Code, Etapes, Icone, Minuteur, QrCode, Squelette],
  templateUrl: './attente.html',
})
export class Attente {
  private readonly parcours = inject(Parcours);
  private readonly router = inject(Router);

  protected readonly etapes = ETAPES;
  protected readonly demande = signal<Demande | null>(null);
  /** Le serveur n'a pas répondu au dernier essai ; l'écran continue d'essayer. */
  protected readonly coupure = signal(false);
  protected readonly annulation = signal(false);
  protected readonly fin = computed(() => FINS[this.demande()?.etat ?? 'EN_ATTENTE_ACCORD']);

  constructor() {
    void this.actualiser();
    periodiquement(INTERVALLE, () => {
      if (!this.fin()) {
        void this.actualiser();
      }
    });
  }

  protected async actualiser(): Promise<void> {
    try {
      const demande = await firstValueFrom(this.parcours.suivre());
      this.coupure.set(false);
      if (demande.dossier_ouvert) {
        await this.router.navigateByUrl('/recuperation', { replaceUrl: true });
      } else {
        this.demande.set(demande);
      }
    } catch (erreur) {
      if (lireProbleme(erreur).statut === 404) {
        // Aucune demande dans ce navigateur : il n'y a rien à attendre.
        await this.router.navigateByUrl('/commencer', { replaceUrl: true });
      } else {
        this.coupure.set(true);
      }
    }
  }

  protected async annuler(): Promise<void> {
    this.annulation.set(true);
    try {
      await firstValueFrom(this.parcours.abandonner());
    } catch {
      // Sans réponse du serveur, la demande expirera d'elle-même : l'usager peut repartir.
    }
    await this.router.navigateByUrl('/commencer', { replaceUrl: true });
  }
}
