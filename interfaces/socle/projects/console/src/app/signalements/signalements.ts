import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule, NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Select } from 'primeng/select';
import {
  Action,
  ActionsLigne,
  Alerte,
  Bouton,
  Cellule,
  Champ,
  ColonneTableau,
  DatePipe,
  Dialogue,
  Etiquette,
  Saisie,
  Tableau,
} from '@sira/ui';
import { Envoi } from '../commun/envoi';
import { compter } from '../commun/libelles';
import { ListeServeur } from '../commun/liste-serveur';
import { LigneJournal, libelleType, nomDe } from '../journal/lignes';
import { Droits } from '../roles';
import { Signalement, Signalements, estTraite, verifierReponse } from './signalements.service';

/** Signalements des citoyens : lire le motif, puis répondre. */
@Component({
  selector: 'co-signalements',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    ReactiveFormsModule,
    Action,
    ActionsLigne,
    Alerte,
    Bouton,
    Cellule,
    Champ,
    DatePipe,
    Dialogue,
    Etiquette,
    Saisie,
    Select,
    Tableau,
  ],
  templateUrl: './signalements.html',
})
export class EcranSignalements {
  private readonly signalements = inject(Signalements);
  protected readonly droits = inject(Droits);
  protected readonly estTraite = estTraite;

  protected readonly choixEtats = [
    { valeur: 'A_TRAITER', libelle: 'À traiter' },
    { valeur: 'TRAITE', libelle: 'Traités' },
    { valeur: '', libelle: 'Tous' },
  ];

  protected readonly colonnes: ColonneTableau[] = [
    { cle: 'cree_le', libelle: 'Reçu le' },
    { cle: 'motif', libelle: 'Motif' },
    { cle: 'ligne', libelle: 'Accès contesté' },
    { cle: 'etat', libelle: 'État' },
    { cle: 'suite', libelle: 'Réponse' },
  ];

  protected readonly liste = new ListeServeur(
    (parametres) => this.signalements.chercher(parametres),
    { etat: 'A_TRAITER' },
  );
  protected readonly compte = (total: number) => compter(total, ['signalement', 'signalements']);

  protected readonly choisi = signal<Signalement | null>(null);
  protected readonly ouvert = signal(false);
  protected readonly reponse = inject(NonNullableFormBuilder).control('');
  protected readonly envoi = new Envoi();
  protected readonly annonce = signal<string | null>(null);

  /** L'accès contesté, en quelques mots. */
  protected acces(ligne: LigneJournal): string | null {
    const parties = [libelleType(ligne.type), nomDe(ligne.service), nomDe(ligne.jeu)];
    return parties.filter(Boolean).join(' · ');
  }

  protected repondre(signalement: Signalement): void {
    this.choisi.set(signalement);
    this.reponse.reset();
    this.envoi.erreurs.set({});
    this.envoi.echec.set(null);
    this.ouvert.set(true);
  }

  protected envoyer(): void {
    const signalement = this.choisi();
    if (!signalement) {
      return;
    }
    const reponse = this.reponse.value.trim();
    const refus = verifierReponse(reponse);
    this.envoi.lancer(
      refus ? { reponse: refus } : {},
      () => this.signalements.traiter(signalement.id, reponse),
      () => {
        this.ouvert.set(false);
        this.annonce.set('Réponse enregistrée. Le citoyen la verra dans son espace.');
        this.liste.recharger();
      },
    );
  }
}
