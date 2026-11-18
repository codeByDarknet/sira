import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { lireProbleme, nombreDePages } from '@sira/coquille';
import {
  Alerte,
  Bouton,
  Cellule,
  Champ,
  ColonneTableau,
  DatePipe,
  EtatVide,
  Etiquette,
  Icone,
  Saisie,
  Squelette,
  Tableau,
} from '@sira/ui';
import { catchError, of } from 'rxjs';
import { Autorisations } from '../acces/autorisations';
import { focusSur } from '../commun/focus';
import { FORMES, compter } from '../commun/libelles';
import { LigneJournal, Reference } from '../commun/modeles';
import { Journal } from './journal';
import { TYPES, decrireIssue } from './libelles';
import { Signalement } from './signalement';

const VIDE = { taille: 1, total: 0 };
const CODE_SERVICE = /^[a-z0-9][a-z0-9-]{0,63}$/;

/**
 * « Qui a consulté mes informations » : tout ce qui s'est passé, refus et échecs compris.
 * Les filtres vivent dans l'adresse : le bouton « retour » du navigateur les respecte.
 */
@Component({
  selector: 'ec-historique',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Alerte,
    Bouton,
    Cellule,
    Champ,
    DatePipe,
    EtatVide,
    Etiquette,
    Icone,
    Saisie,
    Signalement,
    Squelette,
    Tableau,
  ],
  templateUrl: './historique.html',
})
export class Historique {
  private readonly journal = inject(Journal);
  private readonly autorisations = inject(Autorisations);
  private readonly routeur = inject(Router);
  private readonly focus = focusSur();

  /** Paramètres de recherche de l'adresse, reliés par le routeur. */
  readonly service = input<string>();
  readonly type = input<string>();
  readonly page = input<string>();

  protected readonly types = TYPES;
  /** Les gabarits de cellule reçoivent une ligne non typée : la forme passe par une fonction. */
  protected readonly forme = (ligne: LigneJournal) => (ligne.jeu ? FORMES[ligne.jeu.niveau] : '');
  protected readonly decrire = decrireIssue;
  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'horodatage', libelle: 'Date' },
    { cle: 'service', libelle: 'Service' },
    { cle: 'information', libelle: 'Information' },
    { cle: 'issue', libelle: 'Résultat' },
    { cle: 'recours', libelle: 'Un problème ?' },
  ];

  /** Une valeur d'adresse inattendue est ignorée plutôt qu'envoyée au serveur. */
  protected readonly filtre = computed(() => ({
    page: Math.max(1, Math.trunc(Number(this.page())) || 1),
    type: TYPES.find((choix) => choix.type === this.type())?.type,
    service: CODE_SERVICE.test(this.service() ?? '') ? this.service() : undefined,
  }));
  protected readonly filtree = computed(() => !!this.filtre().type || !!this.filtre().service);

  protected readonly resultat = rxResource({
    params: () => this.filtre(),
    stream: ({ params }) => this.journal.lister(params),
  });
  protected readonly pages = computed(() => nombreDePages(this.resultat.value() ?? VIDE));
  protected readonly decompte = computed(() => compter(this.resultat.value()?.total ?? 0, 'ligne'));
  protected readonly probleme = computed(() =>
    this.resultat.error() ? lireProbleme(this.resultat.error()).message : null,
  );

  /** Services proposés au filtre : ceux à qui le citoyen a donné un accès, un jour. */
  private readonly servicesConnus = rxResource({
    stream: () => this.autorisations.services().pipe(catchError(() => of<Reference[]>([]))),
    defaultValue: [],
  });
  /** Le service de l'adresse reste proposé même s'il n'a jamais reçu d'accès. */
  protected readonly services = computed(() => {
    const connus = this.servicesConnus.value();
    const choisi = this.filtre().service;
    // Une ressource en erreur refuse qu'on lise sa valeur.
    const lignes = this.resultat.hasValue() ? this.resultat.value().elements : [];
    const nomme = lignes.find((ligne) => ligne.service.code === choisi);
    return !choisi || connus.some((service) => service.code === choisi)
      ? connus
      : [...connus, { code: choisi, nom: nomme?.service.nom ?? choisi }];
  });

  protected readonly aSignaler = signal<LigneJournal | null>(null);
  protected readonly signale = signal(false);

  protected filtrer(choix: { service?: string; type?: string; page?: number }): void {
    this.signale.set(false);
    void this.routeur.navigate([], {
      // Changer de filtre ramène à la première page.
      queryParams: { page: null, ...videVersNul(choix) },
      queryParamsHandling: 'merge',
    });
  }

  protected apresSignalement(): void {
    this.aSignaler.set(null);
    this.signale.set(true);
    this.resultat.reload();
    this.focus('main sira-alerte');
  }
}

function videVersNul(choix: Record<string, string | number | undefined>) {
  return Object.fromEntries(Object.entries(choix).map(([cle, valeur]) => [cle, valeur || null]));
}
