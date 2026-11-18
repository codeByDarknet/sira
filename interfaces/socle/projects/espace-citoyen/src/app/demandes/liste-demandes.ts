import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Bouton, Carte, Etiquette, Minuteur } from '@sira/ui';
import { Demande } from '../commun/modeles';

/** Demandes qui attendent une réponse : une carte par demande, avec son échéance. */
@Component({
  selector: 'ec-liste-demandes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Bouton, Carte, Etiquette, Minuteur],
  template: `<ul class="ec-liste">
    @for (demande of demandes(); track demande.id) {
      <li>
        <sira-carte [titre]="demande.service.nom" [niveauTitre]="niveauTitre()" teintee>
          <sira-etiquette carte-etat etat="attention">Attend votre réponse</sira-etiquette>
          <p>{{ demande.demarche.libelle }}</p>
          <sira-minuteur [echeance]="demande.expire_le" />
          <a carte-actions siraBouton [routerLink]="['/accord', demande.id]">
            Lire la demande<span class="sira-masque"> de {{ demande.service.nom }}</span>
          </a>
        </sira-carte>
      </li>
    }
  </ul>`,
})
export class ListeDemandes {
  readonly demandes = input.required<readonly Demande[]>();
  readonly niveauTitre = input<2 | 3>(2);
}
