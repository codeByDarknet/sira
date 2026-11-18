import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { identifiantUnique } from '../commun/identifiant';
import { Icone } from '../icone/icone';

/** Groupe de champs liés (`fieldset` et `legend`), avec une erreur qui porte sur l'ensemble. */
@Component({
  selector: 'sira-groupe-formulaire',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone],
  template: `<fieldset
    class="sira-groupe"
    [class.sira-groupe--erreur]="!!erreur()"
    [attr.aria-describedby]="erreur() ? idErreur : null"
  >
    <legend class="sira-groupe__legende">{{ legende() }}</legend>
    @if (aide()) {
      <p class="sira-champ__aide">{{ aide() }}</p>
    }
    @if (erreur()) {
      <p class="sira-champ__erreur" [id]="idErreur">
        <sira-icone nom="circle-alert" />
        <span><span class="sira-masque">Erreur : </span>{{ erreur() }}</span>
      </p>
    }
    <ng-content />
  </fieldset>`,
})
export class GroupeFormulaire {
  readonly legende = input.required<string>();
  readonly aide = input<string>();
  readonly erreur = input<string | null>();

  protected readonly idErreur = identifiantUnique('sira-groupe-erreur');
}
