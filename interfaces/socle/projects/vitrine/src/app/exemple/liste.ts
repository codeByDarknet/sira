import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { lireProbleme, nombreDePages } from '@sira/coquille';
import {
  Alerte,
  Bouton,
  Cellule,
  Champ,
  ColonneTableau,
  DatePipe,
  EtatVide,
  Etiquette,
  Icone,
  Pagination,
  Saisie,
  Squelette,
  Tableau,
} from '@sira/ui';
import { Demarches } from './demarches';

@Component({
  selector: 'vi-liste',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    Alerte,
    Bouton,
    Cellule,
    Champ,
    DatePipe,
    EtatVide,
    Etiquette,
    Icone,
    Pagination,
    Saisie,
    Squelette,
    Tableau,
  ],
  templateUrl: './liste.html',
})
export class Liste {
  private readonly demarches = inject(Demarches);

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'nom', libelle: 'Démarche' },
    { cle: 'organisme', libelle: 'Organisme' },
    { cle: 'etat', libelle: 'État' },
    { cle: 'cree_le', libelle: 'Ajoutée le' },
  ];

  protected readonly page = signal(1);
  protected readonly recherche = signal('');
  /** Message laissé par l'écran précédent (« La démarche a été ajoutée »). */
  protected readonly confirmation = inject(ActivatedRoute).snapshot.queryParamMap.get('message');

  /** La liste se recharge seule quand la page ou la recherche change. */
  protected readonly resultat = rxResource({
    params: () => ({ page: this.page(), recherche: this.recherche() }),
    stream: ({ params }) => this.demarches.lister(params.page, params.recherche),
  });
  protected readonly pages = computed(() => nombreDePages(this.resultat.value() ?? VIDE));
  protected readonly probleme = computed(() =>
    this.resultat.error() ? lireProbleme(this.resultat.error()).message : null,
  );

  protected chercher(evenement: SubmitEvent, saisie: string): void {
    evenement.preventDefault();
    this.page.set(1);
    this.recherche.set(saisie.trim());
  }
}

const VIDE = { taille: 1, total: 0 };
