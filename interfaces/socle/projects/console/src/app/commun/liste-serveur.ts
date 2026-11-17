import { ResourceRef, computed, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Page, Parametres } from '@sira/coquille';
import { RequeteTableau } from '@sira/ui';
import { Observable } from 'rxjs';
import { messageErreur, valeur } from './chargement';

const PREMIERE: RequeteTableau = { page: 1, taille: 10, recherche: '' };

function memes(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

/**
 * État d'une liste paginée côté serveur : page, taille, recherche, tri et filtres de l'écran
 * partent ensemble au serveur ; le tableau n'affiche que la page reçue.
 *
 *     protected readonly liste = new ListeServeur((p) => this.membres.chercher(p));
 *     <sira-tableau [lignes]="liste.lignes()" [total]="liste.total()" … (requete)="liste.demander($event)" />
 */
export class ListeServeur<T> {
  readonly requete = signal<RequeteTableau>(PREMIERE, { equal: memes });
  readonly filtres = signal<Parametres>({}, { equal: memes });
  readonly resultat: ResourceRef<Page<T> | undefined>;

  readonly lignes = computed<T[]>(() => valeur(this.resultat)?.elements ?? []);
  readonly total = computed(() => valeur(this.resultat)?.total ?? 0);
  readonly page = computed(() => this.requete().page);
  readonly taille = computed(() => this.requete().taille);
  readonly chargement = computed(() => this.resultat.isLoading());
  readonly probleme = computed(() => messageErreur(this.resultat));

  constructor(source: (parametres: Parametres) => Observable<Page<T>>, filtres: Parametres = {}) {
    this.filtres.set(filtres);
    this.resultat = rxResource({
      params: () => parametres(this.requete(), this.filtres()),
      stream: ({ params }) => source(params),
    });
  }

  /** Page, recherche ou tri demandés par le tableau. */
  demander(requete: RequeteTableau): void {
    this.requete.set(requete);
  }

  /** Filtre de l'écran (état, registre…) : retour à la première page. */
  filtrer(cle: string, valeurChoisie: string | null | undefined): void {
    this.filtres.update((filtres) => ({ ...filtres, [cle]: valeurChoisie ?? '' }));
    this.requete.update((requete) => ({ ...requete, page: 1 }));
  }

  recharger(): void {
    this.resultat.reload();
  }

  /**
   * Après une création : retour à la première page, où la nouvelle entrée apparaît en tête (le
   * serveur classe de la plus récente à la plus ancienne). Le tri choisi à la main est oublié.
   */
  revenirAuDebut(): void {
    const actuelle = this.requete();
    if (actuelle.page === 1 && !actuelle.tri) {
      this.resultat.reload();
      return;
    }
    this.requete.set({ ...actuelle, page: 1, tri: undefined, sens: undefined });
  }
}

/** Les paramètres non vides, prêts pour l'adresse. */
function parametres(requete: RequeteTableau, filtres: Parametres): Parametres {
  const tous: Parametres = {
    ...filtres,
    page: requete.page,
    taille: requete.taille,
    recherche: requete.recherche,
    tri: requete.tri,
    sens: requete.sens,
  };
  return Object.fromEntries(
    Object.entries(tous).filter(
      ([, valeur]) => valeur !== '' && valeur !== null && valeur !== undefined,
    ),
  );
}
