import {
  ChangeDetectionStrategy,
  Component,
  ResourceRef,
  computed,
  inject,
  input,
  model,
} from '@angular/core';
import { Page, nombreDePages } from '@sira/coquille';
import { Alerte, Bouton, EtatVide, NomIcone, Pagination, Squelette } from '@sira/ui';
import { Annonces } from './annonces';
import { messageErreur, valeur } from './chargement';

/**
 * Enveloppe commune des listes : message de l'écran précédent, chargement, erreur avec
 * nouvel essai, liste vide, pagination. L'écran ne fournit que le tableau.
 */
@Component({
  selector: 'co-liste',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alerte, Bouton, EtatVide, Pagination, Squelette],
  template: `@if (annonce) {
      <sira-alerte type="succes">{{ annonce }}</sira-alerte>
    }
    <div aria-live="polite">
      @if (probleme(); as message) {
        <sira-alerte type="erreur" titre="Liste indisponible">
          <p>{{ message }}</p>
          <button siraBouton variante="secondaire" type="button" (click)="ressource().reload()">
            Réessayer
          </button>
        </sira-alerte>
      } @else if (charge(); as liste) {
        @if (liste.total === 0) {
          <sira-etat-vide [titre]="vide()" [icone]="icone()">
            <ng-content select="[liste-vide]" />
          </sira-etat-vide>
        } @else {
          <ng-content />
          <sira-pagination [page]="liste.page" [pages]="pages()" (changement)="page.set($event)" />
        }
      } @else {
        <sira-squelette [lignes]="5" [libelle]="chargement()" />
      }
    </div>`,
})
export class Liste {
  readonly ressource = input.required<ResourceRef<Page<unknown> | undefined>>();
  /** Titre affiché quand la liste est vide. */
  readonly vide = input.required<string>();
  readonly icone = input<NomIcone>('inbox');
  readonly chargement = input('Chargement de la liste');
  readonly page = model(1);

  protected readonly annonce = inject(Annonces).prendre();
  protected readonly probleme = computed(() => messageErreur(this.ressource()));
  protected readonly charge = computed(() => valeur(this.ressource()));
  protected readonly pages = computed(() =>
    nombreDePages(this.charge() ?? { taille: 1, total: 0 }),
  );
}
