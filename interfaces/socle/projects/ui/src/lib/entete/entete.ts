import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  booleanAttribute,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Bouton } from '../bouton/bouton';
import { EtatMenu } from '../compte/barre-haute';
import { Icone, NomIcone } from '../icone/icone';

export interface LienNavigation {
  libelle: string;
  chemin: string;
  /** Vrai pour un chemin qui ne doit être « courant » que s'il correspond exactement. */
  exact?: boolean;
  /** Icône du lien dans le menu latéral. */
  icone?: NomIcone;
}

/**
 * En-tête d'application : lien d'évitement, marque, menu principal, usager connecté et bouton
 * de déconnexion. Deux dispositions :
 *  - `laterale` : barre latérale fixe (espace citoyen, console, registres), menu à icônes et
 *    compte en bas ; sur petit écran, elle devient une barre en haut ;
 *  - par défaut : barre horizontale blanche (sites des démarches), menu au centre, compte à droite.
 * `plein` est gardé pour compatibilité : il ne fait plus que mettre la marque en valeur.
 */
@Component({
  selector: 'sira-entete',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Bouton, Icone],
  templateUrl: './entete.html',
})
export class Entete {
  readonly nom = input('Sira');
  /** Ligne sous le nom : « Espace citoyen », « Administration »… */
  readonly service = input<string>();
  /** Icône du service ; sans elle, le signe de Sira est affiché. */
  readonly signe = input<NomIcone>();
  readonly plein = input(false, { transform: booleanAttribute });
  /** Barre latérale (applications de gestion et espace citoyen). */
  readonly laterale = input(false, { transform: booleanAttribute });
  readonly liens = input<readonly LienNavigation[]>([]);
  /** Nom de l'usager connecté, s'il est connu (l'espace citoyen ne le reçoit pas). */
  readonly utilisateur = input<string | null>();
  /** Session ouverte : affiche le compte et le bouton de déconnexion, même sans nom. */
  readonly connecte = input(false, { transform: booleanAttribute });
  readonly deconnexion = output();

  /** Deux lettres pour la pastille du compte : « Wendkuni Aïcha KABORÉ » donne « WK ». */
  protected readonly initiales = computed(() => {
    const mots = (this.utilisateur() ?? '').split(/\s+/).filter(Boolean);
    const lettres = mots.length > 1 ? [mots[0], mots[mots.length - 1]] : mots;
    return lettres.map((mot) => mot.charAt(0).toLocaleUpperCase('fr')).join('') || '·';
  });

  private readonly document = inject(DOCUMENT);
  /** Menu latéral replié : icônes seules (bouton de la barre du haut). */
  protected readonly menu = inject(EtatMenu);

  /** Avec une base `/`, l'ancre `#contenu` rechargerait l'accueil : on déplace le focus à la main. */
  protected allerAuContenu(evenement: Event): void {
    evenement.preventDefault();
    this.document.getElementById('contenu')?.focus();
  }
}
