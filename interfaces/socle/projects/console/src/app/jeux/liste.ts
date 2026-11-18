import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
import { valeur } from '../commun/chargement';
import { Confirmation } from '../commun/confirmation';
import { Fenetre } from '../commun/fenetre';
import { CHOIX_ACTIF, compter, libelleActif } from '../commun/libelles';
import { ListeServeur } from '../commun/liste-serveur';
import { Noms } from '../commun/noms';
import { Registres } from '../registres/registres';
import { Droits } from '../roles';
import { AssistantJeu } from './assistant';
import { Jeu, Jeux } from './jeux';
import { NIVEAUX, libelleNiveau, phraseContenu } from './regle';

/**
 * Liste des questions, filtrée par registre et par forme côté serveur. Création et modification
 * par l'assistant, dans une fenêtre.
 */
@Component({
  selector: 'co-liste-jeux',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    RouterLink,
    Action,
    ActionsLigne,
    Alerte,
    AssistantJeu,
    Bouton,
    Cellule,
    Confirmation,
    Etiquette,
    Fenetre,
    Icone,
    Select,
    Tableau,
  ],
  templateUrl: './liste.html',
})
export class ListeJeux {
  private readonly jeux = inject(Jeux);
  protected readonly droits = inject(Droits);
  protected readonly libelleActif = libelleActif;
  protected readonly libelleNiveau = libelleNiveau;
  protected readonly phraseContenu = phraseContenu;
  protected readonly choixNiveaux = [
    { code: '', libelle: 'Toutes les formes' },
    ...NIVEAUX.map((niveau) => ({ code: niveau.code, libelle: niveau.libelle })),
  ];

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'libelle', libelle: 'Question' },
    { cle: 'registre', libelle: 'Registre', filtre: false },
    { cle: 'niveau', libelle: 'Forme', filtre: false },
    { cle: 'contenu', libelle: 'Ce que le service reçoit', tri: false, filtre: false },
    { cle: 'actif', libelle: 'État' },
  ];

  private readonly catalogueRegistres = inject(Registres);
  private readonly tousLesRegistres = rxResource({ stream: () => this.catalogueRegistres.tous() });
  protected readonly choixRegistres = computed(() => [
    { code: '', nom: 'Tous les registres' },
    ...(valeur(this.tousLesRegistres) ?? []).map((registre) => ({
      code: registre.code,
      nom: registre.nom,
    })),
  ]);

  /** La fiche d'un registre mène ici avec son code déjà choisi. */
  protected readonly liste = new ListeServeur((parametres) => this.jeux.chercher(parametres), {
    registre: inject(ActivatedRoute).snapshot.queryParamMap.get('registre') ?? '',
  });
  protected readonly choixActif = CHOIX_ACTIF;
  protected readonly compte = (total: number) => compter(total, ['question', 'questions']);
  protected readonly noms = inject(Noms);

  protected readonly fenetre = signal<{ code?: string } | null>(null);
  protected readonly aDesactiver = signal<Jeu | null>(null);
  protected readonly message = signal<string | null>(null);

  protected readonly desactiver = () => {
    const jeu = this.aDesactiver();
    return jeu ? this.jeux.desactiver(jeu.code) : of(null);
  };

  protected creer(): void {
    this.message.set(null);
    this.fenetre.set({});
  }

  protected apresPublication(jeu: Jeu, code?: string): void {
    this.fenetre.set(null);
    this.message.set(
      code ? `Question « ${jeu.libelle} » modifiée.` : `Question « ${jeu.libelle} » publiée.`,
    );
    if (code) {
      this.liste.recharger();
    } else {
      this.liste.revenirAuDebut();
    }
  }

  protected apresDesactivation(): void {
    this.message.set(`Question « ${this.aDesactiver()?.libelle ?? ''} » désactivée.`);
    this.aDesactiver.set(null);
    this.liste.recharger();
  }
}
