import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { lireProbleme } from '@sira/coquille';
import {
  Alerte,
  ColonneTableau,
  DatePipe,
  Fil,
  Lien,
  Squelette,
  Synthese,
  SyntheseLigne,
  Tableau,
} from '@sira/ui';
import { focusSur } from '../commun/focus';
import { FORMES } from '../commun/libelles';
import { Autorisations } from './autorisations';
import { EtatAcces } from './etat-acces';
import { Retrait } from './retrait';
import { decrireUtilisation } from './utilisation';

/** Détail d'un accès : tout ce que le service peut consulter, et le moyen de l'arrêter. */
@Component({
  selector: 'ec-detail-acces',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    Alerte,
    DatePipe,
    EtatAcces,
    Fil,
    Lien,
    Retrait,
    Squelette,
    Synthese,
    SyntheseLigne,
    Tableau,
  ],
  templateUrl: './detail.html',
})
export class DetailAcces {
  private readonly autorisations = inject(Autorisations);
  private readonly focus = focusSur();

  /** Paramètre `:id` de la route. */
  readonly id = input.required<string>();

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'libelle', libelle: 'Information' },
    { cle: 'forme', libelle: 'Forme' },
    { cle: 'source', libelle: 'Source' },
  ];
  protected readonly autorisation = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.autorisations.lire(params),
  });
  protected readonly probleme = computed(() =>
    this.autorisation.error() ? lireProbleme(this.autorisation.error()).message : null,
  );
  /** Dernier élément du fil d'Ariane ; une ressource en erreur refuse qu'on lise sa valeur. */
  protected readonly nomService = computed(() =>
    this.autorisation.hasValue() ? this.autorisation.value().service.nom : 'Détail',
  );
  protected readonly utilisation = computed(() =>
    this.autorisation.hasValue() ? decrireUtilisation(this.autorisation.value()) : '',
  );
  /** Une ligne par information : son nom, sa forme et l'administration qui la détient. */
  protected readonly informations = computed(() =>
    this.autorisation.hasValue()
      ? this.autorisation.value().jeux.map((jeu) => ({
          libelle: jeu.libelle,
          forme: FORMES[jeu.niveau],
          source: jeu.registre.nom,
        }))
      : [],
  );
  protected readonly retire = signal(false);

  protected apresRetrait(): void {
    this.retire.set(true);
    this.autorisation.reload();
    this.focus('main sira-alerte');
  }
}
