import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { lireProbleme } from '@sira/coquille';
import { Alerte, Bouton, EtatVide, Etapes, Etiquette, Icone, Squelette } from '@sira/ui';
import { firstValueFrom } from 'rxjs';
import { ETAPES } from '../parcours/etapes';
import { IdentifiantSaisi } from '../parcours/identifiant';
import { nomRegistre } from '../parcours/libelles';
import { Dossier, Parcours } from '../parcours/parcours';
import { periodiquement } from '../parcours/periodique';
import { progression } from '../parcours/progression';

const INTERVALLE = 2500;

/**
 * Récupération des informations auprès des registres, une ligne par information. L'écran se met
 * à jour seul ; la personne peut continuer sans attendre un registre qui ne répond pas.
 */
@Component({
  selector: 'ps-recuperation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Alerte, Bouton, EtatVide, Etapes, Etiquette, Icone, Squelette],
  templateUrl: './recuperation.html',
})
export class Recuperation {
  private readonly parcours = inject(Parcours);
  private readonly router = inject(Router);

  protected readonly etapes = ETAPES;
  protected readonly nomRegistre = nomRegistre;

  protected readonly demarche = rxResource({ stream: () => this.parcours.demarche() });
  protected readonly dossier = signal<Dossier | null>(null);
  protected readonly absent = signal(false);
  protected readonly coupure = signal(false);
  /** Les délais se comptent depuis l'arrivée sur cet écran, juste après l'accord. */
  private readonly debut = Date.now();
  private readonly maintenant = signal(this.debut);

  protected readonly lignes = computed(() => {
    const jeux = this.demarche.hasValue() ? this.demarche.value().jeux : [];
    const dossier = this.dossier();
    const depuis = this.maintenant() - this.debut;
    return progression(jeux, dossier?.verifications ?? null, depuis);
  });
  protected readonly recues = computed(
    () => this.lignes().filter((ligne) => ligne.texte === 'Reçu').length,
  );
  protected readonly terminee = computed(
    () => this.dossier() !== null && this.lignes().every((ligne) => !ligne.attendue),
  );

  constructor() {
    // L'identifiant a servi à ouvrir la demande : l'écran l'oublie.
    inject(IdentifiantSaisi).oublier();
    void this.actualiser();
    periodiquement(INTERVALLE, () => {
      this.maintenant.set(Date.now());
      if (!this.terminee() && !this.absent()) {
        void this.actualiser();
      }
    });
  }

  protected async actualiser(): Promise<void> {
    try {
      this.dossier.set(await firstValueFrom(this.parcours.dossier()));
      this.maintenant.set(Date.now());
      this.coupure.set(false);
    } catch (erreur) {
      if (lireProbleme(erreur).statut === 404) {
        this.absent.set(true);
      } else {
        this.coupure.set(true);
      }
    }
  }

  protected continuer(): void {
    void this.router.navigateByUrl('/dossier');
  }
}
