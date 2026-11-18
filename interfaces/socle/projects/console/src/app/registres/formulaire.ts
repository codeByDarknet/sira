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
import { CODE } from '../commun/libelles';
import { Membres } from '../membres/membres';
import { Registre, Registres } from './registres';

type SaisieRegistre = Omit<Registre, 'actif'>;

/**
 * Ajout et modification d'un registre, dans une fenêtre : le même formulaire, le code figé en
 * modification. L'écran hôte ferme la fenêtre sur `termine` ou `annule`.
 */
@Component({
  selector: 'co-formulaire-registre',
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
export class FormulaireRegistre {
  private readonly registres = inject(Registres);
  private readonly membres = inject(Membres);
  /** Présent en modification seulement. */
  readonly code = input<string>();
  readonly termine = output<Registre>();
  readonly annule = output();

  protected readonly existant = rxResource({
    params: () => this.code(),
    stream: ({ params }) => this.registres.lire(params),
  });
  protected readonly introuvable = computed(() => messageErreur(this.existant));

  private readonly tousLesMembres = rxResource({ stream: () => this.membres.tous() });
  /** Seul un membre qui fournit des informations peut tenir un registre. */
  protected readonly teneurs = computed(() =>
    (valeur(this.tousLesMembres) ?? []).filter((membre) => membre.roles.includes('REGISTRE')),
  );

  protected readonly formulaire = inject(NonNullableFormBuilder).group<SaisieRegistre>({
    code: '',
    nom: '',
    membre: '',
    adresse: '',
    transport: 'DIRECT',
  });
  protected readonly envoi = new Envoi();

  constructor() {
    effect(() => {
      const registre = valeur(this.existant);
      if (registre) {
        this.formulaire.patchValue(registre);
        this.formulaire.controls.code.disable();
        // Changer l'adresse du connecteur est une décision de l'autorité, sur la fiche du registre.
        this.formulaire.controls.adresse.disable();
      }
    });
  }

  protected enregistrer(): void {
    const brut = this.formulaire.getRawValue();
    const saisie: SaisieRegistre = {
      ...brut,
      code: brut.code.trim(),
      nom: brut.nom.trim(),
      adresse: brut.adresse.trim(),
    };
    const code = this.code();
    const registre: Registre = { ...saisie, actif: valeur(this.existant)?.actif ?? true };
    this.envoi.lancer(
      verifier(saisie),
      () => (code ? this.registres.remplacer(code, registre) : this.registres.creer(registre)),
      (enregistre) => this.termine.emit(enregistre),
    );
  }
}

export function verifier(saisie: SaisieRegistre): Erreurs {
  const erreurs: Erreurs = {};
  if (!CODE.test(saisie.code)) {
    erreurs['code'] = 'Saisissez un code en minuscules, chiffres et tirets. Exemple : etat-civil';
  }
  if (!saisie.nom) {
    erreurs['nom'] = 'Saisissez le nom du registre.';
  }
  if (!saisie.membre) {
    erreurs['membre'] = 'Choisissez le membre qui le tient.';
  }
  return { ...erreurs, ...verifierAdresse(saisie.adresse) };
}

/** Le connecteur n'est joint qu'en liaison chiffrée : une adresse `http://` serait refusée. */
export function verifierAdresse(adresse: string): Erreurs {
  return URL.canParse(adresse) && new URL(adresse).protocol === 'https:'
    ? {}
    : {
        adresse:
          'Saisissez une adresse qui commence par https://. Exemple : https://connecteur-etat-civil:8443',
      };
}
