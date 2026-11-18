import { Noms } from '../commun/noms';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Alerte, Bouton, Etiquette, Fil, Icone, Lien, NomIcone, Squelette } from '@sira/ui';
import { messageErreur, valeur } from '../commun/chargement';
import { DecisionAdresse } from '../commun/decision-adresse';
import { Confirmation } from '../commun/confirmation';
import { Fenetre } from '../commun/fenetre';
import { libelleActif } from '../commun/libelles';
import { EtatRegistreVue } from '../reseau/etat-registre';
import { Reseau } from '../reseau/reseau';
import { Droits } from '../roles';
import { FormulaireRegistre, verifierAdresse } from './formulaire';
import { operateursPour } from '../jeux/regle';
import { ChampSchema, Registre, Registres, TypeChamp } from './registres';

interface Nature {
  libelle: string;
  icone: NomIcone;
}

const TYPES: Record<TypeChamp, Nature> = {
  TEXTE: { libelle: 'Texte', icone: 'file-text' },
  DATE: { libelle: 'Date', icone: 'clock' },
  NOMBRE: { libelle: 'Nombre', icone: 'list' },
  BOOLEEN: { libelle: 'Oui ou non', icone: 'circle-check' },
};

@Component({
  selector: 'co-detail-registre',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    Alerte,
    Bouton,
    Confirmation,
    DecisionAdresse,
    EtatRegistreVue,
    Etiquette,
    Fenetre,
    Fil,
    FormulaireRegistre,
    Icone,
    Lien,
    Squelette,
  ],
  templateUrl: './detail.html',
})
export class DetailRegistre {
  protected readonly noms = inject(Noms);
  private readonly registres = inject(Registres);
  private readonly reseau = inject(Reseau);
  protected readonly droits = inject(Droits);
  protected readonly libelleActif = libelleActif;

  readonly code = input.required<string>();

  protected readonly resultat = rxResource({
    params: () => this.code(),
    stream: ({ params }) => this.registres.lire(params),
  });
  protected readonly fiche = computed(() => valeur(this.resultat));
  protected readonly probleme = computed(() => messageErreur(this.resultat));

  private readonly etatReseau = rxResource({ stream: () => this.reseau.etat() });
  /** L'état de la liaison est un complément : s'il manque, la fiche reste lisible. */
  protected readonly etat = computed(() =>
    valeur(this.etatReseau)?.registres.find((registre) => registre.code === this.code()),
  );

  /** Ce que le connecteur du registre accepte de laisser sortir (sa politique locale). */
  private readonly lectureSchema = rxResource({
    params: () => this.code(),
    stream: ({ params }) => this.registres.schema(params),
  });
  protected readonly schema = computed(() => valeur(this.lectureSchema));
  protected readonly schemaIndisponible = computed(() => messageErreur(this.lectureSchema));

  protected type(champ: ChampSchema): Nature {
    return TYPES[champ.type] ?? { libelle: champ.type, icone: 'info' };
  }

  /** Ce qu'un service peut recevoir de cette information, dit simplement. */
  protected recu(champ: ChampSchema): string {
    if (champ.niveau !== 'STATUT') {
      return 'Le service peut recevoir la valeur.';
    }
    const exemples = operateursPour(champ).map((operateur) => operateur.exemple);
    return exemples.length > 0
      ? `Vérification seulement, la valeur ne sort pas. Par exemple : ${exemples[0]}`
      : 'Vérification seulement, la valeur ne sort pas.';
  }

  protected readonly modification = signal(false);
  protected readonly desactivation = signal(false);
  protected readonly message = signal<string | null>(null);

  protected readonly desactiver = () => this.registres.desactiver(this.code());
  protected readonly verifierAdresse = verifierAdresse;
  protected readonly changerAdresse = (adresse: string) =>
    this.registres.changerAdresse(this.code(), adresse);

  protected apresModification(registre: Registre): void {
    this.modification.set(false);
    this.message.set(`Registre « ${registre.nom} » modifié.`);
    this.resultat.reload();
  }

  protected apresDesactivation(): void {
    this.message.set('Le registre a été désactivé.');
    this.resultat.reload();
  }
}
