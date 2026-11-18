import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { lireProbleme } from '@sira/coquille';
import {
  Action,
  Alerte,
  DatePipe,
  EtatVide,
  Icone,
  Indicateur,
  NomIcone,
  Squelette,
  TonIndicateur,
  formaterDateHeure,
} from '@sira/ui';
import { catchError, map, of, switchMap, timer } from 'rxjs';
import { messageErreur, valeur } from '../commun/chargement';
import { Habilitations } from '../habilitations/habilitations';
import { EtatRegistreVue } from '../reseau/etat-registre';
import { EtatReseau, Reseau } from '../reseau/reseau';
import { Signalements } from '../signalements/signalements.service';

/** L'état du réseau se relit seul : une panne de registre se voit sans recharger la page. */
export const PERIODE_RAFRAICHISSEMENT_MS = 15_000;

const NOMBRE = new Intl.NumberFormat('fr-FR');

/** Accueil de la console : ce qui demande une action, l'activité du jour, l'état du réseau. */
@Component({
  selector: 'co-tableau-de-bord',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    Action,
    Alerte,
    DatePipe,
    EtatRegistreVue,
    EtatVide,
    Icone,
    Indicateur,
    Squelette,
  ],
  templateUrl: './tableau-de-bord.html',
})
export class TableauDeBord {
  private readonly reseau = inject(Reseau);

  protected readonly reseauActuel = signal<EtatReseau | null>(null);
  protected readonly reseauLuLe = signal<Date | null>(null);
  /** Dernier relevé manqué : les blocs gardent le relevé précédent, et l'écran le dit. */
  protected readonly reseauInjoignable = signal<string | null>(null);

  /** La file d'attente commune, en une phrase. */
  protected readonly phraseFile = computed(() => {
    const file = this.reseauActuel()?.file;
    if (!file || file.a_traiter === 0) {
      return 'file d’attente vide';
    }
    const attente =
      file.a_traiter === 1 ? '1 réponse en attente' : `${file.a_traiter} réponses en attente`;
    return file.plus_ancien_le
      ? `${attente}, depuis le ${formaterDateHeure(file.plus_ancien_le)}`
      : attente;
  });

  private readonly statistiques = rxResource({ stream: () => this.reseau.statistiques() });
  protected readonly statistiquesIndisponibles = computed(() => messageErreur(this.statistiques));
  protected readonly chiffres = computed(() => {
    const jour = valeur(this.statistiques);
    return jour
      ? {
          piecesEvitees: NOMBRE.format(jour.pieces_evitees_total),
          duJour: [
            {
              libelle: 'Demandes du jour',
              valeur: NOMBRE.format(jour.demandes_jour),
              icone: 'send',
              ton: 'violet',
              note: 'Services qui ont demandé l’accord d’un citoyen',
            },
            {
              libelle: 'Accords du jour',
              valeur: NOMBRE.format(jour.accords_jour),
              icone: 'circle-check',
              ton: 'vert',
              note: 'Citoyens qui ont accepté',
            },
            {
              libelle: 'Refus du jour',
              valeur: NOMBRE.format(jour.refus_jour),
              icone: 'ban',
              ton: 'rouge',
              note: 'Citoyens qui ont refusé',
            },
            {
              libelle: 'Consultations du jour',
              valeur: NOMBRE.format(jour.consultations_jour),
              icone: 'eye',
              ton: 'orange',
              note: 'Questions posées aux registres',
            },
          ] satisfies {
            libelle: string;
            valeur: string;
            icone: NomIcone;
            ton: TonIndicateur;
            note: string;
          }[],
        }
      : null;
  });

  private readonly signalementsRecus = inject(Signalements);
  private readonly dossiers = inject(Habilitations);
  /** Un compteur qui manque ne masque pas le reste de l'accueil : il vaut `null`, et l'écran le dit. */
  private readonly aTraiter = rxResource({
    stream: () => this.signalementsRecus.compterATraiter().pipe(catchError(() => of(null))),
  });
  private readonly aDecider = rxResource({
    stream: () => this.dossiers.compter({ etat: 'SOUMISE' }).pipe(catchError(() => of(null))),
  });
  protected readonly signalements = computed(() => valeur(this.aTraiter) ?? 0);
  protected readonly habilitations = computed(() => valeur(this.aDecider) ?? 0);
  protected readonly alertesIncompletes = computed(
    () => valeur(this.aTraiter) === null || valeur(this.aDecider) === null,
  );

  constructor() {
    timer(0, PERIODE_RAFRAICHISSEMENT_MS)
      .pipe(
        switchMap(() =>
          this.reseau.etat().pipe(
            map((etat) => ({ etat, panne: null })),
            catchError((erreur: unknown) =>
              of({ etat: null, panne: lireProbleme(erreur).message }),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ etat, panne }) => {
        this.reseauInjoignable.set(panne);
        if (etat) {
          this.reseauActuel.set(etat);
          this.reseauLuLe.set(new Date());
        }
      });
  }
}
