import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { lireProbleme } from '@sira/coquille';
import { Alerte, Bouton, EtatVide, Onglets, Panneau, Squelette } from '@sira/ui';
import { Paginator } from 'primeng/paginator';
import { focusSur } from '../commun/focus';
import { Autorisation } from '../commun/modeles';
import { Autorisations, MAXIMUM_TERMINES } from './autorisations';
import { CarteAcces } from './carte-acces';

/** « Mes accès » : les services qui peuvent consulter des informations, et ceux qui ne le peuvent plus. */
@Component({
  selector: 'ec-liste-acces',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alerte, Bouton, CarteAcces, EtatVide, Onglets, Paginator, Panneau, Squelette],
  templateUrl: './liste.html',
})
export class ListeAcces {
  private readonly autorisations = inject(Autorisations);
  private readonly focus = focusSur();

  protected readonly onglets = [
    { id: 'en-cours', libelle: 'En cours' },
    { id: 'termines', libelle: 'Terminés' },
  ];
  protected readonly maximum = MAXIMUM_TERMINES;
  protected readonly onglet = signal('en-cours');
  protected readonly page = signal(1);

  protected readonly enCours = rxResource({
    params: () => this.page(),
    stream: ({ params }) => this.autorisations.enCours(params),
  });
  /** Chargés seulement quand l'onglet est ouvert. */
  protected readonly termines = rxResource({
    params: () => (this.onglet() === 'termines' ? true : undefined),
    stream: () => this.autorisations.termines(),
  });
  protected readonly problemeEnCours = computed(() =>
    this.enCours.error() ? lireProbleme(this.enCours.error()).message : null,
  );
  protected readonly problemeTermines = computed(() =>
    this.termines.error() ? lireProbleme(this.termines.error()).message : null,
  );

  /** Nom du service dont l'accès vient d'être retiré. */
  protected readonly retire = signal<string | null>(null);

  protected apresRetrait(autorisation: Autorisation): void {
    this.retire.set(autorisation.service.nom);
    this.page.set(1);
    this.enCours.reload();
    this.termines.reload();
    this.focus('main sira-alerte');
  }
}
