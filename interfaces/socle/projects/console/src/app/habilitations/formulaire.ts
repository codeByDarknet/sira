import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import {
  Alerte,
  Bouton,
  Champ,
  FiltreFrappe,
  GroupeFormulaire,
  Icone,
  ResumeErreurs,
  Saisie,
  Squelette,
} from '@sira/ui';
import { FloatLabel } from 'primeng/floatlabel';
import { Select } from 'primeng/select';
import { messageErreur, valeur } from '../commun/chargement';
import { Envoi, Erreurs } from '../commun/envoi';
import { Demarches } from '../demarches/demarches';
import { Jeux } from '../jeux/jeux';
import { libelleNiveau } from '../jeux/regle';
import { Membres } from '../membres/membres';
import { Habilitation, Habilitations, SaisieHabilitation } from './habilitations';
import { parRegistre, questionsARevoir } from './regles';

const JUSTIFICATION_MINIMALE = 20;

/**
 * Préparation d'une habilitation par l'instructeur, dans une fenêtre : elle reste un brouillon
 * jusqu'à sa soumission. L'écran hôte ferme la fenêtre sur `termine` ou `annule`.
 */
@Component({
  selector: 'co-formulaire-habilitation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    Alerte,
    Bouton,
    Champ,
    FiltreFrappe,
    FloatLabel,
    GroupeFormulaire,
    Icone,
    ResumeErreurs,
    Select,
    Saisie,
    Squelette,
  ],
  templateUrl: './formulaire.html',
})
export class FormulaireHabilitation {
  private readonly habilitations = inject(Habilitations);
  protected readonly libelleNiveau = libelleNiveau;

  /** Présent en modification seulement. */
  readonly id = input<string>();
  readonly termine = output<Habilitation>();
  readonly annule = output();

  protected readonly existant = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.habilitations.lire(params),
  });
  protected readonly introuvable = computed(() => messageErreur(this.existant));

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    service: '',
    demarche: '',
    justification: '',
  });
  private readonly service = toSignal(this.formulaire.controls.service.valueChanges, {
    initialValue: '',
  });
  /** Codes des questions cochées. */
  protected readonly choisies = signal<readonly string[]>([]);

  private readonly catalogueMembres = inject(Membres);
  private readonly tousLesMembres = rxResource({ stream: () => this.catalogueMembres.tous() });
  protected readonly services = computed(() =>
    (valeur(this.tousLesMembres) ?? []).filter(
      (membre) => membre.actif && membre.roles.includes('SERVICE'),
    ),
  );

  private readonly catalogueDemarches = inject(Demarches);
  private readonly toutesLesDemarches = rxResource({
    stream: () => this.catalogueDemarches.tous(),
  });
  /** Une démarche appartient à un service : on ne propose que les siennes. */
  protected readonly demarches = computed(() =>
    (valeur(this.toutesLesDemarches) ?? []).filter(
      (demarche) => demarche.actif && demarche.service === this.service(),
    ),
  );

  private readonly catalogueJeux = inject(Jeux);
  private readonly publiees = rxResource({ stream: () => this.catalogueJeux.tous() });
  private readonly proposees = computed(() =>
    (valeur(this.publiees) ?? []).filter((jeu) => jeu.actif || this.choisies().includes(jeu.code)),
  );
  protected readonly registres = computed(() => parRegistre(this.proposees()));
  protected readonly aRevoir = computed(() => questionsARevoir(this.choisies(), this.proposees()));
  protected readonly listesIndisponibles = computed(
    () =>
      messageErreur(this.tousLesMembres) ??
      messageErreur(this.toutesLesDemarches) ??
      messageErreur(this.publiees),
  );

  protected readonly envoi = new Envoi();

  constructor() {
    effect(() => {
      const habilitation = valeur(this.existant);
      if (habilitation) {
        this.formulaire.setValue({
          service: habilitation.service,
          demarche: habilitation.demarche,
          justification: habilitation.justification,
        });
        this.choisies.set(habilitation.jeux);
      }
    });
  }

  protected changerDeService(): void {
    this.formulaire.controls.demarche.reset();
  }

  protected cocher(code: string, coche: boolean): void {
    this.choisies.update((codes) =>
      coche ? [...codes, code] : codes.filter((autre) => autre !== code),
    );
  }

  protected enregistrer(): void {
    const saisie: SaisieHabilitation = {
      ...this.formulaire.getRawValue(),
      justification: this.formulaire.controls.justification.value.trim(),
      jeux: [...this.choisies()],
    };
    const id = this.id();
    this.envoi.lancer(
      verifier(saisie),
      () => (id ? this.habilitations.remplacer(id, saisie) : this.habilitations.creer(saisie)),
      (enregistree) => this.termine.emit(enregistree),
    );
  }
}

export function verifier(saisie: SaisieHabilitation): Erreurs {
  const erreurs: Erreurs = {};
  if (!saisie.service) {
    erreurs['service'] = 'Choisissez le service qui demande.';
  }
  if (!saisie.demarche) {
    erreurs['demarche'] = 'Choisissez une démarche.';
  }
  if (saisie.jeux.length === 0) {
    erreurs['jeux'] = 'Cochez au moins une question.';
  }
  if (saisie.justification.length < JUSTIFICATION_MINIMALE) {
    erreurs['justification'] = 'Expliquez pourquoi ces questions sont nécessaires.';
  }
  return erreurs;
}
