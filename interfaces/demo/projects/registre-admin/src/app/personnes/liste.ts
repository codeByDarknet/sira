import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { lireProbleme, Session } from '@sira/coquille';
import {
  Action,
  ActionsLigne,
  Alerte,
  Bouton,
  Cellule,
  ColonneTableau,
  DatePipe,
  Dialogue,
  EtatVide,
  Icone,
  RequeteTableau,
  Squelette,
  Tableau,
} from '@sira/ui';
import { Personnes, ResumePersonne } from './personnes';

@Component({
  selector: 'ra-liste',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    Action,
    ActionsLigne,
    Alerte,
    Bouton,
    Cellule,
    DatePipe,
    Dialogue,
    EtatVide,
    Icone,
    Squelette,
    Tableau,
  ],
  templateUrl: './liste.html',
})
export class Liste {
  /** Ce que le rôle de l'agent lui permet : boutons montrés ou cachés. */
  protected readonly session = inject(Session);
  private readonly personnes = inject(Personnes);

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'nom', libelle: 'Nom' },
    { cle: 'prenoms', libelle: 'Prénoms' },
    { cle: 'date_naissance', libelle: 'Date de naissance' },
    { cle: 'identifiant', libelle: 'Identifiant unique' },
  ];

  protected readonly page = signal(1);
  protected readonly recherche = signal('');

  protected readonly resultat = rxResource({
    params: () => ({ page: this.page(), recherche: this.recherche() }),
    stream: ({ params }) => this.personnes.lister(params.page, params.recherche),
  });
  protected readonly confirmation = signal(
    inject(ActivatedRoute).snapshot.queryParamMap.get('message'),
  );
  protected readonly aSupprimer = signal<ResumePersonne | null>(null);
  protected readonly suppression = signal(false);
  protected readonly echecSuppression = signal<string | null>(null);
  protected readonly probleme = computed(() =>
    this.resultat.error() ? lireProbleme(this.resultat.error()).message : null,
  );

  /** Page ou recherche demandée par le tableau : une nouvelle recherche repart de la page 1. */
  protected demander(requete: RequeteTableau): void {
    const recherche = requete.recherche.trim();
    if (recherche !== this.recherche()) {
      this.recherche.set(recherche);
      this.page.set(1);
    } else {
      this.page.set(requete.page);
    }
  }

  protected supprimer(personne: ResumePersonne): void {
    this.suppression.set(true);
    this.echecSuppression.set(null);
    this.personnes.supprimer(personne.id).subscribe({
      next: () => {
        this.suppression.set(false);
        this.aSupprimer.set(null);
        this.confirmation.set(`${personne.nom} ${personne.prenoms} a été retiré du registre.`);
        this.resultat.reload();
      },
      error: (erreur: unknown) => {
        this.suppression.set(false);
        this.echecSuppression.set(lireProbleme(erreur).message);
      },
    });
  }
}
