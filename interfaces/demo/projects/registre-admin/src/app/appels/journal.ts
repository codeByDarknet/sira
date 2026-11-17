import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { lireProbleme, nombreDePages } from '@sira/coquille';
import {
  Alerte,
  Bouton,
  Cellule,
  ColonneTableau,
  DatePipe,
  EtatVide,
  Etiquette,
  Icone,
  Squelette,
  Tableau,
} from '@sira/ui';
import { LIBELLES_MODE } from '../pannes/panne';
import { Appel, Appels } from './appels';

@Component({
  selector: 'ra-journal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alerte, Bouton, Cellule, DatePipe, EtatVide, Etiquette, Icone, Squelette, Tableau],
  templateUrl: './journal.html',
})
export class Journal {
  private readonly appels = inject(Appels);

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'horodatage', libelle: 'Reçu le' },
    { cle: 'type', libelle: 'Demande' },
    { cle: 'statut', libelle: 'Réponse' },
    { cle: 'duree_ms', libelle: 'Durée', numerique: true },
    { cle: 'mode', libelle: 'État du registre' },
    { cle: 'origine', libelle: 'Origine' },
  ];

  protected readonly page = signal(1);
  protected readonly resultat = rxResource({
    params: () => this.page(),
    stream: ({ params }) => this.appels.lister(params),
  });
  protected readonly pages = computed(() => nombreDePages(this.resultat.value() ?? VIDE));
  protected readonly probleme = computed(() =>
    this.resultat.error() ? lireProbleme(this.resultat.error()).message : null,
  );

  /** Revient aux appels les plus récents. */
  protected actualiser(): void {
    if (this.page() === 1) {
      this.resultat.reload();
    } else {
      this.page.set(1);
    }
  }

  protected etat(appel: Appel): string {
    return LIBELLES_MODE[appel.mode];
  }

  protected duree(dureeMs: number): string {
    return dureeMs < 1000
      ? `${dureeMs}\u00a0ms`
      : `${(dureeMs / 1000).toFixed(1).replace('.', ',')}\u00a0s`;
  }
}

const VIDE = { taille: 1, total: 0 };
