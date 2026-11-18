import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  TemplateRef,
  booleanAttribute,
  computed,
  contentChild,
  contentChildren,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Skeleton } from 'primeng/skeleton';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { identifiantUnique } from '../commun/identifiant';
import { Icone } from '../icone/icone';

export interface ColonneTableau {
  cle: string;
  libelle: string;
  numerique?: boolean;
  /**
   * Filtre par valeur sur cette colonne. Par défaut, une colonne affichée telle quelle (sans
   * gabarit `siraCellule`) et qui prend peu de valeurs différentes reçoit un filtre ; `false` le
   * retire, `true` l'impose.
   */
  filtre?: boolean;
  /** Libellé d'une valeur dans la liste du filtre, quand la cellule affiche autre chose. */
  libelleFiltre?: (valeur: string) => string;
  /** Tri en cliquant sur l'en-tête : actif par défaut, `false` le retire. */
  tri?: boolean;
}

/** Gabarit d'une cellule : `<ng-template siraCellule="etat" let-ligne>…</ng-template>`. */
@Directive({ selector: 'ng-template[siraCellule]' })
export class Cellule {
  readonly cle = input.required<string>({ alias: 'siraCellule' });
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- le contexte suit le type des lignes du tableau hôte
  readonly gabarit: TemplateRef<{ $implicit: any }> = inject(TemplateRef);
}

/**
 * Boutons d'action d'une ligne, dans une dernière colonne « Actions » :
 * `<ng-template siraActions let-ligne><button siraAction="eye" libelle="Voir" …></button></ng-template>`.
 */
@Directive({ selector: 'ng-template[siraActions]' })
export class ActionsLigne {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- le contexte suit le type des lignes du tableau hôte
  readonly gabarit: TemplateRef<{ $implicit: any }> = inject(TemplateRef);
}

interface Filtre {
  cle: string;
  libelle: string;
  options: { libelle: string; valeur: string }[];
}

/**
 * Ce que l'écran doit demander au serveur, en mode serveur : page (à partir de 1), taille,
 * texte cherché, colonne et sens du tri.
 */
export interface RequeteTableau {
  page: number;
  taille: number;
  recherche: string;
  tri?: string;
  sens?: 'asc' | 'desc';
}

/** Au-delà de ce nombre de valeurs différentes, une colonne n'a pas de filtre automatique. */
const VALEURS_MAX_FILTRE = 12;

/**
 * Tableau de données (PrimeNG Table) avec recherche, filtres et pagination PrimeNG.
 * Deux modes :
 *  - mode serveur (`total` renseigné) : chaque page, recherche ou tri part au serveur par
 *    `requete` ; le tableau n'affiche que les lignes reçues. C'est le mode des listes ;
 *  - mode local : recherche, filtres et pagination dans les lignes reçues (petites listes).
 * Sous 640 px de large, chaque ligne devient une carte (libellé de colonne devant la valeur).
 */
@Component({
  selector: 'sira-tableau',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    NgTemplateOutlet,
    Icone,
    IconField,
    InputIcon,
    InputText,
    Select,
    Skeleton,
    TableModule,
  ],
  templateUrl: './tableau.html',
})
export class Tableau<T extends object> {
  readonly legende = input.required<string>();
  /** Titre visible en haut à gauche du tableau (sinon la légende reste pour les lecteurs d'écran). */
  readonly titre = input<string>();
  readonly legendeMasquee = input(false, { transform: booleanAttribute });
  readonly colonnes = input.required<readonly ColonneTableau[]>();
  readonly lignes = input.required<readonly T[]>();
  /** Barre de recherche et filtres ; `outils="false"` les retire (tableau de deux lignes). */
  readonly outils = input(true, { transform: booleanAttribute });
  readonly parPage = input(10);
  /** Nombre total de lignes côté serveur : sa présence active le mode serveur. */
  readonly total = input<number | null>(null);
  /** Chargement en cours (mode serveur) : le tableau garde ses lignes et montre l'attente. */
  readonly chargement = input(false, { transform: booleanAttribute });
  /** Page affichée (mode serveur, à partir de 1) : un filtre de l'écran ramène à la page 1. */
  readonly pageCourante = input(1);
  readonly requete = output<RequeteTableau>();
  protected readonly premiereLigne = computed(() =>
    this.serveur() ? (this.pageCourante() - 1) * this.parPage() : this.premier(),
  );

  protected readonly serveur = computed(() => this.total() !== null);
  private tri: { champ?: string; sens?: 'asc' | 'desc' } = {};
  private minuterie?: ReturnType<typeof setTimeout>;

  private readonly cellules = contentChildren(Cellule);
  protected readonly actions = contentChild(ActionsLigne);
  protected readonly nombreColonnes = computed(
    () => this.colonnes().length + (this.actions() ? 1 : 0),
  );
  protected readonly gabarits = computed(
    () => new Map(this.cellules().map((cellule) => [cellule.cle(), cellule.gabarit])),
  );

  protected readonly prefixe = identifiantUnique('sira-tableau');
  protected readonly recherche = signal('');
  protected readonly choix = signal<Record<string, string | null>>({});
  protected readonly premier = signal(0);

  protected readonly filtres = computed<Filtre[]>(() =>
    (this.serveur() ? [] : this.colonnes())
      .filter((colonne) => colonne.filtre !== false && !colonne.numerique)
      .map((colonne) => {
        const valeurs = [
          ...new Set(
            this.lignes()
              .map((ligne) => this.brute(ligne, colonne.cle))
              .filter((valeur): valeur is string => valeur !== null && valeur !== ''),
          ),
        ].sort((a, b) => a.localeCompare(b, 'fr'));
        return { colonne, valeurs };
      })
      .filter(({ colonne, valeurs }) => {
        const automatique =
          !this.gabarits().has(colonne.cle) &&
          valeurs.length > 1 &&
          valeurs.length <= VALEURS_MAX_FILTRE;
        return colonne.filtre === true ? valeurs.length > 1 : automatique;
      })
      .map(({ colonne, valeurs }) => ({
        cle: colonne.cle,
        libelle: colonne.libelle,
        options: valeurs.map((valeur) => ({
          valeur,
          libelle: colonne.libelleFiltre ? colonne.libelleFiltre(valeur) : valeur,
        })),
      })),
  );

  protected readonly visibles = computed(() => {
    if (this.serveur()) {
      return this.lignes();
    }
    const termes = normaliser(this.recherche()).split(/\s+/).filter(Boolean);
    const choix = Object.entries(this.choix()).filter(([, valeur]) => valeur !== null);
    return this.lignes().filter((ligne) => {
      if (choix.some(([cle, valeur]) => this.brute(ligne, cle) !== valeur)) {
        return false;
      }
      if (termes.length === 0) {
        return true;
      }
      const texte = normaliser(textes(ligne).join(' '));
      return termes.every((terme) => texte.includes(terme));
    });
  });

  /** Premier chargement (aucune ligne encore) : des lignes grises tiennent la place. */
  protected readonly squelette = computed(() => this.chargement() && this.visibles().length === 0);
  protected readonly lignesSquelette = Array.from({ length: 5 }, (_, rang) => ({ rang }));
  protected readonly affichees = computed(() =>
    this.squelette() ? this.lignesSquelette : this.visibles(),
  );

  protected readonly filtreActif = computed(
    () => this.recherche() !== '' || Object.values(this.choix()).some((valeur) => valeur !== null),
  );

  constructor() {
    // Mode local : toute nouvelle recherche, tout nouveau filtre ou de nouvelles lignes ramènent
    // à la page 1. En mode serveur, c'est le serveur qui dit quelle page est affichée.
    effect(() => {
      this.visibles();
      if (!this.serveur()) {
        this.premier.set(0);
      }
    });
  }

  /** Page ou tri changé par PrimeNG (mode serveur). */
  protected chargerPage(evenement: TableLazyLoadEvent): void {
    const taille = evenement.rows ?? this.parPage();
    const premier = evenement.first ?? 0;
    this.premier.set(premier);
    const champ = typeof evenement.sortField === 'string' ? evenement.sortField : undefined;
    this.tri = champ ? { champ, sens: evenement.sortOrder === -1 ? 'desc' : 'asc' } : {};
    this.emettre(Math.floor(premier / taille) + 1, taille);
  }

  /** Texte cherché : en mode serveur, la demande part après une courte pause de frappe. */
  protected chercher(texte: string): void {
    this.recherche.set(texte);
    if (!this.serveur()) {
      return;
    }
    clearTimeout(this.minuterie);
    this.minuterie = setTimeout(() => {
      this.premier.set(0);
      this.emettre(1, this.parPage());
    }, 300);
  }

  private emettre(page: number, taille: number): void {
    this.requete.emit({
      page,
      taille,
      recherche: this.recherche().trim(),
      ...(this.tri.champ ? { tri: this.tri.champ, sens: this.tri.sens } : {}),
    });
  }

  protected choisir(cle: string, valeur: string | null): void {
    this.choix.update((choix) => ({ ...choix, [cle]: valeur }));
  }

  protected effacer(): void {
    this.recherche.set('');
    this.choix.set({});
  }

  protected valeur(ligne: T, cle: string): unknown {
    return (ligne as Record<string, unknown>)[cle] ?? '—';
  }

  private brute(ligne: T, cle: string): string | null {
    const valeur = (ligne as Record<string, unknown>)[cle];
    return valeur === null || valeur === undefined || typeof valeur === 'object'
      ? null
      : String(valeur);
  }
}

/** Minuscules, sans accents : « Kaboré » se trouve en tapant « kabore ». */
function normaliser(texte: string): string {
  return texte
    .toLocaleLowerCase('fr')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .trim();
}

/** Toutes les valeurs simples d'une ligne, y compris dans les objets imbriqués. */
function textes(valeur: unknown, profondeur = 0): string[] {
  if (valeur === null || valeur === undefined || profondeur > 3) {
    return [];
  }
  if (typeof valeur === 'object') {
    return Object.values(valeur as Record<string, unknown>).flatMap((v) =>
      textes(v, profondeur + 1),
    );
  }
  return [String(valeur)];
}
