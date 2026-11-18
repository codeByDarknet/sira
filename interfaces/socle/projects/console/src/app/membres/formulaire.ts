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
import { Adresses } from '../commun/adresses';
import { messageErreur, valeur } from '../commun/chargement';
import { Envoi, Erreurs } from '../commun/envoi';
import { CODE } from '../commun/libelles';
import { Membre, Membres, RoleMembre, SaisieMembre } from './membres';

interface SaisieFormulaire {
  code: string;
  nom: string;
  organisme: string;
  service: boolean;
  registre: boolean;
  retours: string;
}

/**
 * Ajout et modification d'un membre, dans une fenêtre : le même formulaire, le code figé en
 * modification. L'écran hôte ferme la fenêtre sur `termine` ou `annule`.
 */
@Component({
  selector: 'co-formulaire-membre',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    Adresses,
    Alerte,
    Bouton,
    Champ,
    FiltreFrappe,
    GroupeFormulaire,
    Icone,
    ResumeErreurs,
    Saisie,
    Squelette,
  ],
  templateUrl: './formulaire.html',
})
export class FormulaireMembre {
  private readonly membres = inject(Membres);

  /** Présent en modification seulement. */
  readonly code = input<string>();
  /** Le membre enregistré, tel que le serveur le renvoie. */
  readonly termine = output<Membre>();
  readonly annule = output();

  protected readonly existant = rxResource({
    params: () => this.code(),
    stream: ({ params }) => this.membres.lire(params),
  });
  protected readonly introuvable = computed(() => messageErreur(this.existant));

  protected readonly formulaire = inject(NonNullableFormBuilder).group({
    code: '',
    nom: '',
    organisme: '',
    service: false,
    registre: false,
    retours: '',
  });
  protected readonly envoi = new Envoi();

  constructor() {
    effect(() => {
      const membre = valeur(this.existant);
      if (membre) {
        this.formulaire.setValue({
          code: membre.code,
          nom: membre.nom,
          organisme: membre.organisme,
          service: membre.roles.includes('SERVICE'),
          registre: membre.roles.includes('REGISTRE'),
          retours: membre.retours_autorises.join('\n'),
        });
        this.formulaire.controls.code.disable();
        // Changer les adresses de retour est une décision de l'autorité, sur la fiche du membre.
        this.formulaire.controls.retours.disable();
      }
    });
  }

  protected enregistrer(): void {
    const saisie = this.formulaire.getRawValue();
    const code = this.code();
    const membre = versMembre(saisie, valeur(this.existant)?.actif ?? true);
    this.envoi.lancer(
      verifier(saisie),
      () => (code ? this.membres.remplacer(code, membre) : this.membres.creer(membre)),
      (enregistre) => this.termine.emit(enregistre),
    );
  }
}

export function lignes(texte: string): string[] {
  return texte
    .split('\n')
    .map((ligne) => ligne.trim())
    .filter(Boolean);
}

export function versMembre(saisie: SaisieFormulaire, actif: boolean): SaisieMembre {
  const roles: RoleMembre[] = [];
  if (saisie.service) {
    roles.push('SERVICE');
  }
  if (saisie.registre) {
    roles.push('REGISTRE');
  }
  return {
    code: saisie.code.trim(),
    nom: saisie.nom.trim(),
    organisme: saisie.organisme.trim(),
    roles,
    // Les adresses de retour n'ont de sens que pour un membre qui demande.
    retours_autorises: saisie.service ? lignes(saisie.retours) : [],
    actif,
  };
}

export function verifier(saisie: SaisieFormulaire): Erreurs {
  const erreurs: Erreurs = {};
  if (!CODE.test(saisie.code.trim())) {
    erreurs['code'] = 'Saisissez un code en minuscules, chiffres et tirets. Exemple : concours';
  }
  if (!saisie.nom.trim()) {
    erreurs['nom'] = 'Saisissez le nom du membre.';
  }
  if (!saisie.organisme.trim()) {
    erreurs['organisme'] = 'Saisissez l’organisme.';
  }
  if (!saisie.service && !saisie.registre) {
    erreurs['roles'] = 'Cochez au moins un rôle.';
  }
  if (saisie.service) {
    Object.assign(erreurs, verifierRetours(saisie.retours));
  }
  return erreurs;
}

export function verifierRetours(texte: string): Erreurs {
  return lignes(texte).every(estAdresseWeb)
    ? {}
    : {
        retours_autorises:
          'Chaque adresse doit être complète et commencer par https:// (sans espace). Exemple : https://concours.gov.bf/retour',
      };
}

/**
 * Adresse complète d'une page : https obligatoire (http seulement pour un site `.localhost` de
 * démonstration), un vrai nom d'hôte, ni espace ni identifiants dans l'adresse.
 */
export function estAdresseWeb(texte: string): boolean {
  if (/\s/.test(texte) || !URL.canParse(texte)) {
    return false;
  }
  const adresse = new URL(texte);
  const local = adresse.hostname === 'localhost' || adresse.hostname.endsWith('.localhost');
  const protocole = adresse.protocol === 'https:' || (adresse.protocol === 'http:' && local);
  const hote = local || /^[a-z0-9-]+(\.[a-z0-9-]+)+$/i.test(adresse.hostname);
  return protocole && hote && !adresse.username && !adresse.password;
}
