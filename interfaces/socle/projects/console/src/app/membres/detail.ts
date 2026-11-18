import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Alerte, Bouton, Etiquette, Fil, Icone, Squelette } from '@sira/ui';
import { messageErreur, valeur } from '../commun/chargement';
import { Confirmation } from '../commun/confirmation';
import { DecisionAdresse } from '../commun/decision-adresse';
import { Fenetre } from '../commun/fenetre';
import { libelleActif } from '../commun/libelles';
import { Droits } from '../roles';
import { FormulaireMembre, lignes, verifierRetours } from './formulaire';
import { Membre, Membres } from './membres';

/** Fiche d'un membre : chaque information avec ce qu'elle veut dire ; modifier, désactiver. */
@Component({
  selector: 'co-detail-membre',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Alerte,
    Bouton,
    Confirmation,
    DecisionAdresse,
    Etiquette,
    Fenetre,
    Fil,
    FormulaireMembre,
    Icone,
    Squelette,
  ],
  templateUrl: './detail.html',
})
export class DetailMembre {
  private readonly membres = inject(Membres);
  protected readonly droits = inject(Droits);
  protected readonly libelleActif = libelleActif;

  readonly code = input.required<string>();

  protected readonly resultat = rxResource({
    params: () => this.code(),
    stream: ({ params }) => this.membres.lire(params),
  });
  protected readonly fiche = computed(() => valeur(this.resultat));
  protected readonly probleme = computed(() => messageErreur(this.resultat));

  protected readonly modification = signal(false);
  protected readonly desactivation = signal(false);
  protected readonly message = signal<string | null>(null);

  protected readonly desactiver = () => this.membres.desactiver(this.code());
  protected readonly verifierRetours = verifierRetours;
  protected readonly changerRetours = (texte: string) =>
    this.membres.changerRetours(this.code(), lignes(texte));

  protected apresModification(membre: Membre): void {
    this.modification.set(false);
    this.message.set(`Membre « ${membre.nom} » modifié.`);
    this.resultat.reload();
  }

  protected apresDesactivation(): void {
    this.message.set('Le membre a été désactivé.');
    this.resultat.reload();
  }
}
