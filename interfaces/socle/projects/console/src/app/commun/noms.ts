import { Injectable, Signal, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Observable, catchError, of } from 'rxjs';
import { Demarches } from '../demarches/demarches';
import { Jeux } from '../jeux/jeux';
import { Membres } from '../membres/membres';
import { Registres } from '../registres/registres';

/**
 * Le nom lisible d'un membre, d'un registre, d'une démarche ou d'une question, à partir de son
 * code : les écrans montrent « Direction de l'état civil » plutôt que « etat-civil ». Les listes
 * sont lues une fois ; sans droit de lecture ou en cas d'erreur, le code reste affiché.
 */
@Injectable({ providedIn: 'root' })
export class Noms {
  private readonly membres = charger(inject(Membres).tous(), (m) => [m.code, m.nom]);
  private readonly registres = charger(inject(Registres).tous(), (r) => [r.code, r.nom]);
  private readonly demarches = charger(inject(Demarches).tous(), (d) => [d.code, d.libelle]);
  private readonly jeux = charger(inject(Jeux).tous(), (j) => [j.code, j.libelle]);

  membre(code: string | null | undefined): string {
    return nommer(this.membres(), code);
  }

  registre(code: string | null | undefined): string {
    return nommer(this.registres(), code);
  }

  demarche(code: string | null | undefined): string {
    return nommer(this.demarches(), code);
  }

  jeu(code: string | null | undefined): string {
    return nommer(this.jeux(), code);
  }
}

function charger<T>(
  source: Observable<T[]>,
  paire: (element: T) => [string, string],
): Signal<Map<string, string>> {
  const elements = toSignal(source.pipe(catchError(() => of([] as T[]))), {
    initialValue: [] as T[],
  });
  return computed(() => new Map(elements().map(paire)));
}

function nommer(noms: Map<string, string>, code: string | null | undefined): string {
  if (!code) {
    return '—';
  }
  return noms.get(code) ?? code;
}
