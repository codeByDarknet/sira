import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import {
  Alerte,
  Bouton,
  Champ,
  FiltreFrappe,
  Icone,
  ResumeErreurs,
  Saisie,
  Squelette,
} from '@sira/ui';
import { FloatLabel } from 'primeng/floatlabel';
import { Select } from 'primeng/select';
import { messageErreur, valeur } from '../commun/chargement';
import { Envoi, Erreurs } from '../commun/envoi';
import { CODE } from '../commun/libelles';
import { Membres } from '../membres/membres';
import { Demarche, Demarches } from './demarches';

interface SaisieDemarche {
  code: string;
  libelle: string;
  finalite: string;
  base_legale: string;
  service: string;
  duree: string;
}

/** Au-delà d'un an, l'accord du citoyen doit être redemandé. */
const DUREE_MAXIMALE_JOURS = 365;

/**
 * Ajout et modification d'une démarche, dans une fenêtre : le même formulaire, le code figé en
 * modification. L'écran hôte ferme la fenêtre sur `termine` ou `annule`.
 */
@Component({
  selector: 'co-formulaire-demarche',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    Alerte,
    Bouton,
    Champ,
    FiltreFrappe,
    FloatLabel,
    Icone,
    ResumeErreurs,
    Saisie,
    Select,
    Squelette,
  ],
  templateUrl: './formulaire.html',
})
export class FormulaireDemarche {
  private readonly demarches = inject(Demarches);
  private readonly membres = inject(Membres);
  /** Présent en modification seulement. */
  readonly code = input<string>();
  readonly termine = output<Demarche>();
  readonly annule = output();

  protected readonly existant = rxResource({
    params: () => this.code(),
    stream: ({ params }) => this.demarches.lire(params),
  });
  protected readonly introuvable = computed(() => messageErreur(this.existant));

  private readonly tousLesMembres = rxResource({ stream: () => this.membres.tous() });
  protected readonly services = computed(() =>
    (valeur(this.tousLesMembres) ?? []).filter((membre) => membre.roles.includes('SERVICE')),
  );

  protected readonly formulaire = inject(NonNullableFormBuilder).group<SaisieDemarche>({
    code: '',
    libelle: '',
    finalite: '',
    base_legale: '',
    service: '',
    duree: '',
  });
  protected readonly envoi = new Envoi();

  constructor() {
    effect(() => {
      const demarche = valeur(this.existant);
      if (demarche) {
        this.formulaire.patchValue({
          ...demarche,
          duree: String(demarche.duree_autorisation_jours),
        });
        this.formulaire.controls.code.disable();
      }
    });
  }

  protected enregistrer(): void {
    const saisie = this.formulaire.getRawValue();
    const code = this.code();
    const demarche: Demarche = {
      code: saisie.code.trim(),
      libelle: saisie.libelle.trim(),
      finalite: saisie.finalite.trim(),
      base_legale: saisie.base_legale.trim(),
      service: saisie.service,
      duree_autorisation_jours: Number(saisie.duree),
      actif: valeur(this.existant)?.actif ?? true,
    };
    this.envoi.lancer(
      verifier(saisie),
      () => (code ? this.demarches.remplacer(code, demarche) : this.demarches.creer(demarche)),
      (enregistree) => this.termine.emit(enregistree),
    );
  }
}

export function verifier(saisie: SaisieDemarche): Erreurs {
  const erreurs: Erreurs = {};
  if (!CODE.test(saisie.code.trim())) {
    erreurs['code'] =
      'Saisissez un code en minuscules, chiffres et tirets. Exemple : concours-direct';
  }
  if (!saisie.libelle.trim()) {
    erreurs['libelle'] = 'Saisissez le nom de la démarche.';
  }
  if (!saisie.service) {
    erreurs['service'] = 'Choisissez un service.';
  }
  if (saisie.finalite.trim().length < 10) {
    erreurs['finalite'] = 'Saisissez la finalité, en une phrase.';
  }
  if (!saisie.base_legale.trim()) {
    erreurs['base_legale'] = 'Saisissez la base légale.';
  }
  const duree = Number(saisie.duree);
  if (
    saisie.duree.trim() === '' ||
    !Number.isInteger(duree) ||
    duree < 0 ||
    duree > DUREE_MAXIMALE_JOURS
  ) {
    erreurs['duree_autorisation_jours'] =
      `Saisissez un nombre de jours entre 0 et ${DUREE_MAXIMALE_JOURS}. 0 pour une seule consultation.`;
  }
  return erreurs;
}
