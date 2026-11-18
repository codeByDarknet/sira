import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { lireProbleme } from '@sira/coquille';
import { Alerte, Bouton, EtatVide, Icone, Squelette } from '@sira/ui';
import { suivre } from '../commun/rafraichissement';
import { Demandes } from './demandes';
import { ListeDemandes } from './liste-demandes';

/** Demandes en attente ; la liste se tient à jour seule, sans rien faire clignoter. */
@Component({
  selector: 'ec-en-attente',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Alerte, Bouton, EtatVide, Icone, ListeDemandes, Squelette],
  templateUrl: './en-attente.html',
})
export class EnAttente {
  private readonly demandes = inject(Demandes);
  private readonly suivi = suivre();

  protected readonly resultat = rxResource({
    stream: () => this.suivi(() => this.demandes.enAttente()),
  });
  protected readonly probleme = computed(() =>
    this.resultat.error() ? lireProbleme(this.resultat.error()).message : null,
  );
}
