import { ChangeDetectionStrategy, Component, effect, inject, input } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Alerte, Bouton, Champ, Saisie } from '@sira/ui';
import { Observable } from 'rxjs';
import { Adresses } from './adresses';
import { Annonces } from './annonces';
import { Envoi, Erreurs } from './envoi';

/**
 * Changement d'une adresse que seule l'autorité décide : où revient le citoyen après son accord,
 * où partent les questions vers un registre. L'instructeur ne la change pas par la modification.
 */
@Component({
  selector: 'co-decision-adresse',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, Adresses, Alerte, Bouton, Champ, Saisie],
  template: `<h2>{{ titre() }}</h2>
    @if (envoi.echec(); as message) {
      <sira-alerte type="erreur" titre="Décision non enregistrée">{{ message }}</sira-alerte>
    }
    <form class="sira-pile" novalidate (ngSubmit)="enregistrer()">
      @if (plusieurs()) {
        <co-adresses [formControl]="saisie" [titre]="libelle()" [identifiant]="champ()" />
        <p class="sira-champ__aide">{{ aide() }}</p>
        @if (envoi.erreurs()[champ()]; as erreur) {
          <p class="sira-champ__erreur">{{ erreur }}</p>
        }
      } @else {
        <sira-champ
          [libelle]="libelle()"
          [aide]="aide()"
          [identifiant]="champ()"
          [erreur]="envoi.erreurs()[champ()]"
        >
          <input siraSaisie [formControl]="saisie" inputmode="url" autocomplete="off" />
        </sira-champ>
      }
      <div class="sira-groupe-boutons">
        <button siraBouton variante="secondaire" type="submit" [enCours]="envoi.enCours()">
          Décider le changement
        </button>
      </div>
    </form>`,
})
export class DecisionAdresse {
  private readonly routeur = inject(Router);
  private readonly annonces = inject(Annonces);

  readonly titre = input.required<string>();
  readonly libelle = input.required<string>();
  readonly aide = input.required<string>();
  /** Nom du champ dans le JSON du serveur : ses erreurs s'affichent sous la saisie. */
  readonly champ = input.required<string>();
  readonly valeur = input.required<string>();
  /** Une adresse par ligne. */
  readonly plusieurs = input(false);
  readonly verifier = input.required<(texte: string) => Erreurs>();
  readonly appel = input.required<(texte: string) => Observable<unknown>>();
  /** Liste où revenir, et message à y afficher. */
  readonly retour = input.required<string>();
  readonly annonce = input.required<string>();

  protected readonly saisie = inject(NonNullableFormBuilder).control('');
  protected readonly envoi = new Envoi();

  constructor() {
    effect(() => this.saisie.setValue(this.valeur()));
  }

  protected enregistrer(): void {
    const texte = this.saisie.value.trim();
    this.envoi.lancer(
      this.verifier()(texte),
      () => this.appel()(texte),
      () => {
        this.annonces.laisser(this.annonce());
        void this.routeur.navigateByUrl(this.retour());
      },
    );
  }
}
