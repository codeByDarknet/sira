import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { lireProbleme } from '@sira/coquille';
import { Bouton, Squelette } from '@sira/ui';
import { firstValueFrom } from 'rxjs';
import { Parcours } from '../parcours/parcours';

/**
 * Retour de Sira après la décision prise sur cet appareil. Le code reçu dans l'adresse est
 * remis au serveur, qui vérifie qu'il s'agit bien de la demande ouverte par ce navigateur.
 */
@Component({
  selector: 'ps-retour',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Bouton, Squelette],
  template: `@if (refus(); as message) {
      <h1>Démarche impossible à reprendre</h1>
      <p class="sira-chapeau">{{ message }}</p>
      <div class="sira-groupe-boutons">
        <a siraBouton routerLink="/commencer">Recommencer</a>
      </div>
    } @else {
      <h1>Retour de Sira</h1>
      <sira-squelette [lignes]="3" libelle="Reprise de votre démarche" />
    }`,
})
export class Retour {
  private readonly parcours = inject(Parcours);
  private readonly router = inject(Router);

  protected readonly refus = signal<string | null>(null);

  constructor() {
    const parametres = inject(ActivatedRoute).snapshot.queryParamMap;
    void this.reprendre(parametres.get('demande'), parametres.get('code_retour'));
  }

  private async reprendre(demande: string | null, code: string | null): Promise<void> {
    if (!demande || !code) {
      this.refus.set('Le lien de retour est incomplet. Rien n’a été transmis.');
      return;
    }
    try {
      const suivie = await firstValueFrom(this.parcours.revenir(demande, code));
      // L'adresse de retour porte le code : elle ne reste pas dans l'historique.
      await this.router.navigateByUrl(suivie.dossier_ouvert ? '/recuperation' : '/accord', {
        replaceUrl: true,
      });
    } catch (erreur) {
      const probleme = lireProbleme(erreur);
      this.refus.set(
        probleme.statut >= 500 || probleme.statut === 0
          ? probleme.message
          : 'Ce lien ne correspond pas à la démarche ouverte ici. Rien n’a été transmis.',
      );
    }
  }
}
