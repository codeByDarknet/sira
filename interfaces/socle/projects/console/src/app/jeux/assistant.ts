import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import {
  Alerte,
  Bouton,
  Champ,
  FiltreFrappe,
  Etapes,
  GroupeFormulaire,
  Icone,
  ResumeErreurs,
  Saisie,
  Squelette,
  Synthese,
  SyntheseLigne,
} from '@sira/ui';
import { map } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { FloatLabel } from 'primeng/floatlabel';
import { MultiSelect } from 'primeng/multiselect';
import { Select } from 'primeng/select';
import { messageErreur, valeur } from '../commun/chargement';
import { Envoi, Erreurs } from '../commun/envoi';
import { ChampSchema, Registres } from '../registres/registres';
import { Jeu, Jeux } from './jeux';
import {
  NIVEAUX,
  SAISIE_VIDE,
  construireJeu,
  decrireOperateurPour,
  formeValeur,
  operateursPour,
  etapes,
  libelleNiveau,
  phraseContenu,
  phraseValidite,
  verifierEtape,
  versSaisie,
} from './regle';

const DERNIERE_ETAPE = 5;

/**
 * Assistant de création d'une question, sans code : registre, forme de la réponse, règle,
 * libellé, puis relecture avant de publier. Affiché dans une fenêtre :
 * l'écran hôte la ferme sur `termine` (question publiée) ou `annule`.
 */
@Component({
  selector: 'co-assistant-jeu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    MultiSelect,
    Alerte,
    Bouton,
    Champ,
    FiltreFrappe,
    Etapes,
    FloatLabel,
    GroupeFormulaire,
    Icone,
    ResumeErreurs,
    Saisie,
    Select,
    Squelette,
    Synthese,
    SyntheseLigne,
  ],
  templateUrl: './assistant.html',
})
export class AssistantJeu {
  private readonly jeux = inject(Jeux);
  private readonly injecteur = inject(Injector);
  private readonly constructeur = inject(NonNullableFormBuilder);

  protected readonly niveaux = NIVEAUX;
  protected readonly libelleNiveau = libelleNiveau;
  protected readonly phraseValidite = phraseValidite;

  /** Présent en modification seulement. */
  readonly code = input<string>();
  readonly termine = output<Jeu>();
  readonly annule = output();

  protected readonly existant = rxResource({
    params: () => this.code(),
    stream: ({ params }) => this.jeux.lire(params),
  });
  protected readonly introuvable = computed(() => messageErreur(this.existant));
  protected readonly titre = computed(() =>
    this.code() ? 'Modifier la question' : 'Créer une question',
  );

  private readonly catalogueRegistres = inject(Registres);
  private readonly tousLesRegistres = rxResource({ stream: () => this.catalogueRegistres.tous() });
  protected readonly registres = computed(() =>
    (valeur(this.tousLesRegistres) ?? []).filter(
      (registre) => registre.actif || registre.code === this.saisie().registre,
    ),
  );
  protected readonly registresIndisponibles = computed(() => messageErreur(this.tousLesRegistres));

  protected readonly formulaire = this.constructeur.group({ ...SAISIE_VIDE });
  protected readonly saisie = toSignal(
    this.formulaire.valueChanges.pipe(map(() => this.formulaire.getRawValue())),
    { initialValue: this.formulaire.getRawValue() },
  );

  protected readonly etape = signal(1);
  protected readonly etapes = computed(() => etapes(this.saisie().niveau));
  protected readonly titreEtape = computed(() => this.etapes()[this.etape() - 1]);
  private readonly titreAffiche = viewChild<ElementRef<HTMLElement>>('titreAffiche');

  protected readonly erreurs = signal<Erreurs>({});
  protected readonly resume = computed(() =>
    Object.entries(this.erreurs()).map(([champ, message]) => ({ champ, message })),
  );

  /** Ce que le registre expose, lu en direct sur son connecteur : rien d'autre n'est proposé. */
  // Un `computed` à part : il ne change que si le registre change. Lu directement dans `params`,
  // le formulaire entier relançait la lecture (et effaçait les choix) à chaque frappe.
  private readonly registreChoisi = computed(() => this.saisie().registre || undefined);
  protected readonly schema = rxResource({
    params: () => this.registreChoisi(),
    stream: ({ params }) => this.catalogueRegistres.schema(params),
  });
  protected readonly schemaIndisponible = computed(() => messageErreur(this.schema));
  protected readonly champsSchema = computed(() => valeur(this.schema)?.champs ?? []);
  protected readonly champsTransmissibles = computed(() =>
    this.champsSchema().filter((champ) => champ.niveau !== 'STATUT'),
  );
  protected readonly documentsSchema = computed(() => valeur(this.schema)?.documents ?? []);
  /** Libellé lisible de chaque information et de chaque document du registre. */
  protected readonly libelles = computed<Record<string, string>>(() =>
    Object.fromEntries([
      ...this.champsSchema().map((champ) => [champ.chemin, champ.libelle]),
      ...this.documentsSchema().map((document) => [document.code, document.libelle]),
    ]),
  );
  protected readonly champChoisi = computed<ChampSchema | undefined>(() =>
    this.champsSchema().find((champ) => champ.chemin === this.saisie().champ),
  );
  protected readonly operateurs = computed(() => operateursPour(this.champChoisi()));
  protected readonly operateur = computed(() =>
    decrireOperateurPour(this.saisie().operateur, this.champChoisi()?.type),
  );
  protected readonly forme = computed(() => formeValeur(this.champChoisi(), this.operateur()));
  protected readonly valeursAdmises = computed(() =>
    (this.champChoisi()?.valeurs ?? []).map((v) => ({
      libelle: typeof v === 'boolean' ? (v ? 'Oui' : 'Non') : String(v),
      valeur: String(v),
    })),
  );
  protected readonly ouiNon = [
    { libelle: 'Oui', valeur: 'true' },
    { libelle: 'Non', valeur: 'false' },
  ];
  protected readonly champsCoches = computed(() =>
    this.saisie()
      .champs.split(',')
      .map((champ) => champ.trim())
      .filter(Boolean),
  );
  protected readonly nomRegistre = computed(() => {
    const code = this.saisie().registre;
    return this.registres().find((registre) => registre.code === code)?.nom ?? code;
  });

  /** La question telle qu’elle partira au serveur. */
  protected readonly question = computed<Jeu>(() => {
    const jeu = construireJeu(this.saisie(), valeur(this.existant)?.actif ?? true);
    // En modification, le code ne change jamais, quelle que soit sa forme d'origine.
    return { ...jeu, code: this.code() ?? jeu.code };
  });
  protected readonly apercu = computed(() =>
    Object.keys(verifierEtape(3, this.saisie())).length === 0
      ? phraseContenu(this.question(), this.libelles())
      : null,
  );

  protected readonly publication = new Envoi();
  /** Refus du serveur sur la question publiée. */
  protected readonly refus = computed(() => Object.values(this.publication.erreurs()));

  constructor() {
    effect(() => {
      const jeu = valeur(this.existant);
      if (jeu) {
        this.formulaire.setValue(versSaisie(jeu));
        // Le code d'une question publiée est déjà cité par des habilitations : il reste figé.
        this.formulaire.controls.registre.disable();
        this.formulaire.controls.suffixe.disable();
      }
    });
  }

  /** Une autre information choisie : la condition et la valeur d'avant n'ont plus de sens. */
  protected choisirChamp(): void {
    this.formulaire.patchValue({ operateur: '', valeur: '' });
  }

  protected choisirOperateur(): void {
    this.formulaire.patchValue({ valeur: '' });
  }

  protected cocherChamps(champs: string[] | null): void {
    this.formulaire.controls.champs.setValue((champs ?? []).join(', '));
  }

  protected continuer(): void {
    const erreurs = verifierEtape(this.etape(), this.saisie());
    this.erreurs.set(erreurs);
    if (Object.keys(erreurs).length === 0) {
      this.allerA(this.etape() + 1);
    }
  }

  protected revenir(): void {
    this.erreurs.set({});
    this.allerA(this.etape() - 1);
  }

  protected publier(): void {
    const code = this.code();
    const question = this.question();
    this.publication.lancer(
      {},
      () => (code ? this.jeux.remplacer(code, question) : this.jeux.creer(question)),
      (publiee) => this.termine.emit(publiee),
    );
  }

  private allerA(etape: number): void {
    this.etape.set(Math.min(Math.max(etape, 1), DERNIERE_ETAPE));
    this.publication.erreurs.set({});
    // Le titre de la nouvelle étape reçoit le focus : un lecteur d'écran l'annonce aussitôt.
    afterNextRender(() => this.titreAffiche()?.nativeElement.focus(), {
      injector: this.injecteur,
    });
  }
}
