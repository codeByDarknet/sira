import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  Action,
  ActionsLigne,
  Alerte,
  Bouton,
  Cellule,
  ColonneTableau,
  Etiquette,
  Icone,
  Tableau,
} from '@sira/ui';
import { Select } from 'primeng/select';
import { of } from 'rxjs';
import { Confirmation } from '../commun/confirmation';
import { Fenetre } from '../commun/fenetre';
import { CHOIX_ACTIF, compter, libelleActif } from '../commun/libelles';
import { ListeServeur } from '../commun/liste-serveur';
import { Droits } from '../roles';
import { FormulaireMembre } from './formulaire';
import { libelleRoleMembre, Membre, Membres } from './membres';

/**
 * Liste des membres : voir la fiche, modifier ou désactiver depuis la colonne « Actions » ;
 * ajout et modification dans une fenêtre, sans quitter la liste.
 */
@Component({
  selector: 'co-liste-membres',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    Action,
    ActionsLigne,
    Alerte,
    Bouton,
    Cellule,
    Confirmation,
    Etiquette,
    Fenetre,
    FormulaireMembre,
    Icone,
    Select,
    Tableau,
  ],
  templateUrl: './liste.html',
})
export class ListeMembres {
  private readonly membres = inject(Membres);
  protected readonly droits = inject(Droits);
  protected readonly libelleActif = libelleActif;
  protected readonly libelleRole = libelleRoleMembre;

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'nom', libelle: 'Membre' },
    { cle: 'organisme', libelle: 'Organisme' },
    { cle: 'roles', libelle: 'Rôle', tri: false },
    { cle: 'actif', libelle: 'État' },
  ];

  protected readonly liste = new ListeServeur((parametres) => this.membres.chercher(parametres));
  protected readonly choixActif = CHOIX_ACTIF;
  protected readonly compte = (total: number) => compter(total, ['membre', 'membres']);

  /** Fenêtre ouverte : `{}` pour un ajout, `{ code }` pour une modification. */
  protected readonly fenetre = signal<{ code?: string } | null>(null);
  protected readonly aDesactiver = signal<Membre | null>(null);
  protected readonly message = signal<string | null>(null);

  protected readonly desactiver = () => {
    const membre = this.aDesactiver();
    return membre ? this.membres.desactiver(membre.code) : of(null);
  };

  protected ajouter(): void {
    this.message.set(null);
    this.fenetre.set({});
  }

  protected modifier(code: string): void {
    this.message.set(null);
    this.fenetre.set({ code });
  }

  protected apresEnregistrement(membre: Membre, code?: string): void {
    this.fenetre.set(null);
    this.message.set(
      code ? `Membre « ${membre.nom} » modifié.` : `Membre « ${membre.nom} » ajouté.`,
    );
    if (code) {
      this.liste.recharger();
    } else {
      this.liste.revenirAuDebut();
    }
  }

  protected apresDesactivation(): void {
    this.message.set(`Membre « ${this.aDesactiver()?.nom ?? ''} » désactivé.`);
    this.aDesactiver.set(null);
    this.liste.recharger();
  }
}
