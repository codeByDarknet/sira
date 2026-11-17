import { Injectable, inject } from '@angular/core';
import { Api, Page } from '@sira/coquille';
import { Observable, forkJoin, map } from 'rxjs';
import { Autorisation, EtatAutorisation, Reference } from '../commun/modeles';

const BASE = '/api/autorisations';
/** Borne des accès terminés affichés : au-delà, l'historique garde la trace complète. */
export const MAXIMUM_TERMINES = 50;

export interface AccesTermines {
  elements: Autorisation[];
  /** Vrai quand il existe plus d'accès terminés que ceux affichés. */
  tronque: boolean;
}

/** Accès donnés par le citoyen connecté à des services. */
@Injectable({ providedIn: 'root' })
export class Autorisations {
  private readonly api = inject(Api);

  enCours(page: number): Observable<Page<Autorisation>> {
    return this.lister('ACTIVE', page, 20);
  }

  /** Accès retirés et accès arrivés à leur fin, du plus récemment terminé au plus ancien. */
  termines(): Observable<AccesTermines> {
    return forkJoin([
      this.lister('RETIREE', 1, MAXIMUM_TERMINES),
      this.lister('EXPIREE', 1, MAXIMUM_TERMINES),
    ]).pipe(
      map((pages) => {
        const elements = pages
          .flatMap((page) => page.elements)
          .sort((a, b) => finDe(b).localeCompare(finDe(a)));
        return {
          elements: elements.slice(0, MAXIMUM_TERMINES),
          tronque: pages[0].total + pages[1].total > MAXIMUM_TERMINES,
        };
      }),
    );
  }

  /** Services à qui un accès a été donné un jour, pour le filtre de l'historique. */
  services(): Observable<Reference[]> {
    const etats: EtatAutorisation[] = ['ACTIVE', 'RETIREE', 'EXPIREE'];
    return forkJoin(etats.map((etat) => this.lister(etat, 1, MAXIMUM_TERMINES))).pipe(
      map((pages) => {
        const services = new Map<string, Reference>();
        for (const { service } of pages.flatMap((page) => page.elements)) {
          services.set(service.code, { code: service.code, nom: service.nom });
        }
        return [...services.values()].sort((a, b) => a.nom.localeCompare(b.nom, 'fr'));
      }),
    );
  }

  lire(id: string): Observable<Autorisation> {
    return this.api.lire(`${BASE}/${encodeURIComponent(id)}`);
  }

  retirer(id: string): Observable<void> {
    return this.api.supprimer(`${BASE}/${encodeURIComponent(id)}`);
  }

  private lister(
    etat: EtatAutorisation,
    page: number,
    taille: number,
  ): Observable<Page<Autorisation>> {
    return this.api.lire(BASE, { etat, page, taille });
  }
}

function finDe(autorisation: Autorisation): string {
  return autorisation.retiree_le ?? autorisation.expire_le ?? autorisation.accordee_le;
}
