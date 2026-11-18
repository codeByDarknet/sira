import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Session, lireProbleme } from '@sira/coquille';
import {
  Alerte,
  Bouton,
  Cellule,
  ColonneTableau,
  DatePipe,
  Etiquette,
  Icone,
  Indicateur,
  Squelette,
  Tableau,
} from '@sira/ui';
import { Observable, catchError, forkJoin, of } from 'rxjs';
import { Appels } from '../appels/appels';
import { LIBELLES_MODE, Pannes } from '../pannes/panne';
import { Personnes } from '../personnes/personnes';
import { Registre } from '../registre/registre';

/** Accueil de l'agent : les chiffres du registre et les derniers appels reçus de Sira. */
@Component({
  selector: 'ra-tableau-de-bord',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    Alerte,
    Bouton,
    Cellule,
    DatePipe,
    Etiquette,
    Icone,
    Indicateur,
    Squelette,
    Tableau,
  ],
  templateUrl: './tableau-de-bord.html',
})
export class TableauDeBord {
  private readonly personnes = inject(Personnes);
  private readonly appels = inject(Appels);
  private readonly pannes = inject(Pannes);
  private readonly registre = inject(Registre);
  protected readonly session = inject(Session);

  protected readonly libelles = LIBELLES_MODE;
  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'horodatage', libelle: 'Reçu le', tri: false },
    { cle: 'type', libelle: 'Demande', tri: false },
    { cle: 'statut', libelle: 'Réponse', tri: false },
  ];

  protected readonly donnees = rxResource({
    stream: () =>
      forkJoin({
        personnes: this.siDroit(['PERSONNES_LIRE'], () => this.personnes.lister(1, '')),
        appels: this.siDroit(['APPELS_LIRE'], () => this.appels.lister(1)),
        panne: this.siDroit(['PANNES_LIRE', 'PANNES_GERER'], () => this.pannes.lire()),
        registre: this.registre.description,
      }),
  });
  protected readonly probleme = computed(() =>
    this.donnees.error() ? lireProbleme(this.donnees.error()).message : null,
  );

  /** Un chiffre n'est demandé que si le rôle de l'agent permet de le voir. */
  private siDroit<T>(droits: string[], appel: () => Observable<T>): Observable<T | null> {
    return this.session.peut(...droits) ? appel().pipe(catchError(() => of(null))) : of(null);
  }
}
