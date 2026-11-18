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
import { Noms } from '../commun/noms';
import { Droits } from '../roles';
import { FormulaireRegistre } from './formulaire';
import { Registre, Registres } from './registres';

/** Liste des registres : fiche, questions, modification et désactivation par la colonne Actions. */
@Component({
  selector: 'co-liste-registres',
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
    FormulaireRegistre,
    Icone,
    Select,
    Tableau,
  ],
  templateUrl: './liste.html',
})
export class ListeRegistres {
  private readonly registres = inject(Registres);
  protected readonly droits = inject(Droits);
  protected readonly noms = inject(Noms);
  protected readonly libelleActif = libelleActif;

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'nom', libelle: 'Registre' },
    { cle: 'membre', libelle: 'Administration rattaché' },
    { cle: 'actif', libelle: 'État' },
  ];

  protected readonly liste = new ListeServeur((parametres) => this.registres.chercher(parametres));
  protected readonly choixActif = CHOIX_ACTIF;
  protected readonly compte = (total: number) => compter(total, ['registre', 'registres']);

  protected readonly fenetre = signal<{ code?: string } | null>(null);
  protected readonly aDesactiver = signal<Registre | null>(null);
  protected readonly message = signal<string | null>(null);

  protected readonly desactiver = () => {
    const registre = this.aDesactiver();
    return registre ? this.registres.desactiver(registre.code) : of(null);
  };

  protected ajouter(): void {
    this.message.set(null);
    this.fenetre.set({});
  }

  protected modifier(code: string): void {
    this.message.set(null);
    this.fenetre.set({ code });
  }

  protected apresEnregistrement(registre: Registre, code?: string): void {
    this.fenetre.set(null);
    this.message.set(
      code ? `Registre « ${registre.nom} » modifié.` : `Registre « ${registre.nom} » ajouté.`,
    );
    if (code) {
      this.liste.recharger();
    } else {
      this.liste.revenirAuDebut();
    }
  }

  protected apresDesactivation(): void {
    this.message.set(`Registre « ${this.aDesactiver()?.nom ?? ''} » désactivé.`);
    this.aDesactiver.set(null);
    this.liste.recharger();
  }
}
