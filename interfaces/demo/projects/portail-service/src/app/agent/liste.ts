import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { lireProbleme, nombreDePages } from '@sira/coquille';
import {
  Action,
  ActionsLigne,
  Alerte,
  Bouton,
  Cellule,
  ColonneTableau,
  DatePipe,
  EtatVide,
  Etiquette,
  Squelette,
  Tableau,
} from '@sira/ui';
import { Select } from 'primeng/select';
import { mentionDossier } from '../parcours/libelles';
import { EtatDossier } from '../parcours/parcours';
import { Agent } from './agent';

const ETATS: EtatDossier[] = ['A_EXAMINER', 'EN_VERIFICATION', 'COMPLET'];
const VIDE = { taille: 1, total: 0 };

@Component({
  selector: 'ps-liste',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    Action,
    ActionsLigne,
    Alerte,
    Bouton,
    Cellule,
    DatePipe,
    EtatVide,
    Etiquette,
    Select,
    Squelette,
    Tableau,
  ],
  templateUrl: './liste.html',
})
export class Liste {
  private readonly agent = inject(Agent);

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'numero', libelle: 'Dossier' },
    { cle: 'etat', libelle: 'État' },
    { cle: 'cree_le', libelle: 'Déposé le' },
    { cle: 'modifie_le', libelle: 'Dernière réponse' },
  ];
  protected readonly etats = ETATS.map((etat) => ({ etat, libelle: mentionDossier(etat).texte }));
  protected readonly mention = mentionDossier;
  protected readonly choixEtats = [{ etat: '', libelle: 'Tous les états' }, ...this.etats];

  protected readonly page = signal(1);
  protected readonly etat = signal<EtatDossier | ''>('');

  protected readonly resultat = rxResource({
    params: () => ({ page: this.page(), etat: this.etat() }),
    stream: ({ params }) => this.agent.lister(params.page, params.etat),
  });
  protected readonly pages = computed(() => nombreDePages(this.resultat.value() ?? VIDE));
  protected readonly probleme = computed(() =>
    this.resultat.error() ? lireProbleme(this.resultat.error()).message : null,
  );

  protected filtrer(etat: string): void {
    this.page.set(1);
    this.etat.set(ETATS.find((connu) => connu === etat) ?? '');
  }
}
