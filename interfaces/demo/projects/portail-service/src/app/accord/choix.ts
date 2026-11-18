import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { REDIRECTION, lireProbleme } from '@sira/coquille';
import { Alerte, Bouton, Carte, Etapes, Icone, Lien } from '@sira/ui';
import { firstValueFrom } from 'rxjs';
import { ETAPES } from '../parcours/etapes';
import { IdentifiantSaisi } from '../parcours/identifiant';
import { Canal, Parcours } from '../parcours/parcours';

/**
 * Trois façons de donner son accord dans Sira : ici, en scannant, ou depuis son téléphone.
 * L'identifiant unique saisi dans « Votre demande » sert aux trois.
 */
@Component({
  selector: 'ps-choix',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Alerte, Bouton, Carte, Etapes, Icone, Lien],
  templateUrl: './choix.html',
})
export class Choix {
  private readonly parcours = inject(Parcours);
  private readonly memoire = inject(IdentifiantSaisi);
  private readonly router = inject(Router);
  private readonly rediriger = inject(REDIRECTION);

  protected readonly etapes = ETAPES;
  /** Canal de la demande en cours d'ouverture : les trois boutons attendent ensemble. */
  protected readonly enCours = signal<Canal | null>(null);
  protected readonly probleme = signal<string | null>(null);

  constructor() {
    if (!this.memoire.identifiant()) {
      // Rien à envoyer sans identifiant (page rechargée, lien direct) : retour au formulaire.
      void this.router.navigateByUrl('/commencer', { replaceUrl: true });
    }
  }

  protected async ouvrir(canal: Canal): Promise<void> {
    const identifiant = this.memoire.identifiant();
    if (this.enCours() || !identifiant) {
      return;
    }
    this.enCours.set(canal);
    this.probleme.set(null);
    try {
      const demande = await firstValueFrom(this.parcours.ouvrir(canal, identifiant));
      if (canal === 'REDIRECTION' && demande.url_accord) {
        // La page change : les boutons restent en attente jusqu'au départ.
        this.rediriger(demande.url_accord);
        return;
      }
      await this.router.navigateByUrl('/accord');
    } catch (erreur) {
      const probleme = lireProbleme(erreur);
      this.probleme.set(probleme.champs['identifiant_unique'] ?? probleme.message);
    }
    this.enCours.set(null);
  }
}
