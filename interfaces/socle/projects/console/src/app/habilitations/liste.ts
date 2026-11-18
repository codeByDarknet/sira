import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
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
import { Annonces } from '../commun/annonces';
import { Fenetre } from '../commun/fenetre';
import { compter } from '../commun/libelles';
import { ListeServeur } from '../commun/liste-serveur';
import { Noms } from '../commun/noms';
import { Droits } from '../roles';
import { FormulaireHabilitation } from './formulaire';
import { ETATS_HABILITATION, Habilitation, Habilitations, libelleEtat } from './habilitations';
import { SeparationDesRoles } from './separation';

/**
 * Liste des habilitations, filtrée par état côté serveur. Préparer ou modifier un brouillon se
 * fait dans une fenêtre ; soumettre et décider, sur la fiche.
 */
@Component({
  selector: 'co-liste-habilitations',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    Action,
    ActionsLigne,
    Alerte,
    Bouton,
    Cellule,
    Etiquette,
    Fenetre,
    FormulaireHabilitation,
    Icone,
    Select,
    SeparationDesRoles,
    Tableau,
  ],
  templateUrl: './liste.html',
})
export class ListeHabilitations {
  private readonly habilitations = inject(Habilitations);
  private readonly routeur = inject(Router);
  private readonly annonces = inject(Annonces);
  protected readonly droits = inject(Droits);
  protected readonly libelleEtat = libelleEtat;

  protected readonly choixEtats = [
    { valeur: '', libelle: 'Tous les états' },
    ...ETATS_HABILITATION.map((etat) => ({ valeur: etat, libelle: libelleEtat(etat).texte })),
  ];

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'service', libelle: 'Service et démarche' },
    { cle: 'jeux', libelle: 'Questions autorisées', tri: false },
    { cle: 'instruite_par', libelle: 'Préparée par' },
    { cle: 'etat', libelle: 'État', filtre: false },
  ];

  /** L'autorité arrive sur ce qu'elle doit décider ; les autres voient tout. */
  protected readonly liste = new ListeServeur(
    (parametres) => this.habilitations.chercher(parametres),
    { etat: this.droits.decision() && !this.droits.ecriture() ? 'SOUMISE' : '' },
  );
  protected readonly compte = (total: number) => compter(total, ['habilitation', 'habilitations']);
  protected readonly noms = inject(Noms);

  protected readonly fenetre = signal<{ id?: string } | null>(null);
  protected readonly message = signal<string | null>(null);

  protected preparer(): void {
    this.message.set(null);
    this.fenetre.set({});
  }

  /** Un brouillon se relit puis se soumet : on ouvre sa fiche. */
  protected apresEnregistrement(habilitation: Habilitation): void {
    this.fenetre.set(null);
    this.annonces.laisser('Brouillon enregistré. Relisez-le, puis soumettez-le à l’autorité.');
    void this.routeur.navigate(['/habilitations', habilitation.id]);
  }
}
