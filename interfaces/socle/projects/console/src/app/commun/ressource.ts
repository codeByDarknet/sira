import { inject } from '@angular/core';
import { Api, Page, Parametres } from '@sira/coquille';
import { Observable, map } from 'rxjs';

const TAILLE_PAGE = 20;
/** Taille maximale admise par le serveur : sert aux listes de choix des formulaires. */
const TAILLE_MAXIMALE = 100;

/** Accès à une ressource de la console : même forme d'appels pour toutes. */
export abstract class Ressource<T> {
  protected readonly api = inject(Api);
  protected abstract readonly chemin: string;

  lister(page: number, filtres: Parametres = {}): Observable<Page<T>> {
    return this.api.lire(this.chemin, { ...filtres, page, taille: TAILLE_PAGE });
  }

  /** Une page, avec recherche, tri et filtres, tout fait par le serveur. */
  chercher(parametres: Parametres): Observable<Page<T>> {
    return this.api.lire(this.chemin, parametres);
  }

  /** Les cent premiers éléments, pour remplir une liste de choix. */
  tous(filtres: Parametres = {}): Observable<T[]> {
    return this.api
      .lire<Page<T>>(this.chemin, { ...filtres, page: 1, taille: TAILLE_MAXIMALE })
      .pipe(map((page) => page.elements));
  }

  /** Nombre d'éléments qui répondent aux filtres, sans les charger. */
  compter(filtres: Parametres): Observable<number> {
    return this.api
      .lire<Page<T>>(this.chemin, { ...filtres, page: 1, taille: 1 })
      .pipe(map((page) => page.total));
  }

  lire(code: string): Observable<T> {
    return this.api.lire(this.adresse(code));
  }

  creer(objet: unknown): Observable<T> {
    return this.api.creer(this.chemin, objet);
  }

  remplacer(code: string, objet: unknown): Observable<T> {
    return this.api.remplacer(this.adresse(code), objet);
  }

  /** Le serveur n'efface jamais : il désactive. */
  desactiver(code: string): Observable<void> {
    return this.api.supprimer(this.adresse(code));
  }

  protected adresse(code: string, action = ''): string {
    return `${this.chemin}/${encodeURIComponent(code)}${action}`;
  }
}
