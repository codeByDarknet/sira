import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Select } from 'primeng/select';
import { rxResource } from '@angular/core/rxjs-interop';
import { lireProbleme } from '@sira/coquille';
import {
  Action,
  ActionsLigne,
  Alerte,
  Bouton,
  Cellule,
  ColonneTableau,
  DatePipe,
  Dialogue,
  Etiquette,
  Icone,
  Synthese,
  SyntheseLigne,
  Tableau,
} from '@sira/ui';
import { valeur } from '../commun/chargement';
import { compter } from '../commun/libelles';
import { ListeServeur } from '../commun/liste-serveur';
import { libelleNiveau } from '../jeux/regle';
import { Membres } from '../membres/membres';
import { Registres } from '../registres/registres';
import {
  CompteRendu,
  Journal,
  LIBELLES_TYPE,
  libelleResultat,
  libelleType,
  LigneJournal,
  TypeLigne,
  compteRendu,
  libelleCanal,
  niveauDe,
  nomDe,
} from './lignes';

/** Journal du socle : qui a demandé quoi, quand, pourquoi, avec quel résultat. Jamais la valeur. */
@Component({
  selector: 'co-journal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    Action,
    ActionsLigne,
    Alerte,
    Bouton,
    Cellule,
    DatePipe,
    Dialogue,
    Etiquette,
    Icone,
    Select,
    Synthese,
    SyntheseLigne,
    Tableau,
  ],
  templateUrl: './journal.html',
})
export class EcranJournal {
  private readonly journal = inject(Journal);
  protected readonly types = Object.entries(LIBELLES_TYPE) as [TypeLigne, string][];
  protected readonly libelleType = libelleType;
  protected readonly libelleResultat = libelleResultat;
  protected readonly libelleNiveau = libelleNiveau;
  protected readonly libelleCanal = libelleCanal;
  protected readonly nomDe = nomDe;
  protected readonly niveauDe = niveauDe;

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'horodatage', libelle: 'Date' },
    { cle: 'type', libelle: 'Événement' },
    { cle: 'service', libelle: 'Service' },
    { cle: 'registre', libelle: 'Registre' },
    { cle: 'jeu', libelle: 'Question' },
    { cle: 'resultat', libelle: 'Résultat' },
  ];

  private readonly catalogueMembres = inject(Membres);
  private readonly tousLesMembres = rxResource({ stream: () => this.catalogueMembres.tous() });
  protected readonly services = computed(() =>
    (valeur(this.tousLesMembres) ?? []).filter((membre) => membre.roles.includes('SERVICE')),
  );
  private readonly catalogueRegistres = inject(Registres);
  private readonly tousLesRegistres = rxResource({ stream: () => this.catalogueRegistres.tous() });
  protected readonly registres = computed(() => valeur(this.tousLesRegistres) ?? []);

  protected readonly liste = new ListeServeur((parametres) => this.journal.chercher(parametres));
  protected readonly compte = (total: number) => compter(total, ['ligne', 'lignes']);
  protected readonly verificationOuverte = signal(false);

  /** Ligne affichée dans la fenêtre de détail ; elle y reste le temps de la fermeture. */
  protected readonly choisie = signal<LigneJournal | null>(null);
  protected readonly detailOuvert = signal(false);

  protected readonly verificationEnCours = signal(false);
  protected readonly verification = signal<CompteRendu | null>(null);
  protected readonly verificationImpossible = signal<string | null>(null);

  protected readonly choixTypes = [
    { valeur: '', libelle: 'Tous les événements' },
    ...this.types.map(([valeur, libelle]) => ({ valeur, libelle })),
  ];
  protected readonly choixServices = computed(() => [
    { valeur: '', libelle: 'Tous les services' },
    ...this.services().map((membre) => ({ valeur: membre.code, libelle: membre.nom })),
  ]);
  protected readonly choixRegistres = computed(() => [
    { valeur: '', libelle: 'Tous les registres' },
    ...this.registres().map((registre) => ({ valeur: registre.code, libelle: registre.nom })),
  ]);

  protected ouvrirVerification(): void {
    this.verification.set(null);
    this.verificationImpossible.set(null);
    this.verificationOuverte.set(true);
  }

  protected voir(ligne: LigneJournal): void {
    this.choisie.set(ligne);
    this.detailOuvert.set(true);
  }

  protected verifier(): void {
    this.verificationEnCours.set(true);
    this.verification.set(null);
    this.verificationImpossible.set(null);
    this.journal.verifier().subscribe({
      next: (verification) => {
        this.verificationEnCours.set(false);
        this.verification.set(compteRendu(verification));
      },
      error: (erreur: unknown) => {
        this.verificationEnCours.set(false);
        this.verificationImpossible.set(lireProbleme(erreur).message);
      },
    });
  }
}
