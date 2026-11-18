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
import { Demarche, Demarches, phraseDureeAutorisation } from './demarches';
import { FormulaireDemarche } from './formulaire';

/** Liste des démarches : fiche, modification et désactivation par la colonne Actions. */
@Component({
  selector: 'co-liste-demarches',
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
    FormulaireDemarche,
    Icone,
    Select,
    Tableau,
  ],
  templateUrl: './liste.html',
})
export class ListeDemarches {
  private readonly demarches = inject(Demarches);
  protected readonly droits = inject(Droits);
  protected readonly noms = inject(Noms);
  protected readonly libelleActif = libelleActif;
  protected readonly phraseDuree = phraseDureeAutorisation;

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'libelle', libelle: 'Démarche' },
    { cle: 'service', libelle: 'Service' },
    { cle: 'duree_autorisation_jours', libelle: 'Durée de l’accord', filtre: false },
    { cle: 'actif', libelle: 'État' },
  ];

  protected readonly liste = new ListeServeur((parametres) => this.demarches.chercher(parametres));
  protected readonly choixActif = CHOIX_ACTIF;
  protected readonly compte = (total: number) => compter(total, ['démarche', 'démarches']);

  protected readonly fenetre = signal<{ code?: string } | null>(null);
  protected readonly aDesactiver = signal<Demarche | null>(null);
  protected readonly message = signal<string | null>(null);

  protected readonly desactiver = () => {
    const demarche = this.aDesactiver();
    return demarche ? this.demarches.desactiver(demarche.code) : of(null);
  };

  protected ajouter(): void {
    this.message.set(null);
    this.fenetre.set({});
  }

  protected modifier(code: string): void {
    this.message.set(null);
    this.fenetre.set({ code });
  }

  protected apresEnregistrement(demarche: Demarche, code?: string): void {
    this.fenetre.set(null);
    this.message.set(
      code
        ? `Démarche « ${demarche.libelle} » modifiée.`
        : `Démarche « ${demarche.libelle} » ajoutée.`,
    );
    if (code) {
      this.liste.recharger();
    } else {
      this.liste.revenirAuDebut();
    }
  }

  protected apresDesactivation(): void {
    this.message.set(`Démarche « ${this.aDesactiver()?.libelle ?? ''} » désactivée.`);
    this.aDesactiver.set(null);
    this.liste.recharger();
  }
}
