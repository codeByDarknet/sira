import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Carte, DatePipe, Lien, Synthese, SyntheseLigne } from '@sira/ui';
import { Autorisation } from '../commun/modeles';
import { EtatAcces } from './etat-acces';
import { Retrait } from './retrait';
import { decrireUtilisation } from './utilisation';

/** Un accès dans la liste : à qui, pour quoi, sur quelles informations, jusqu'à quand. */
@Component({
  selector: 'ec-carte-acces',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Carte, DatePipe, EtatAcces, Lien, Retrait, Synthese, SyntheseLigne],
  template: `<sira-carte [titre]="autorisation().service.nom">
    <ec-etat-acces carte-etat [etat]="autorisation().etat" />
    <dl siraSynthese>
      <div siraSyntheseLigne cle="Pour">{{ autorisation().demarche.libelle }}</div>
      <div siraSyntheseLigne cle="Informations">{{ informations() }}</div>
      <div siraSyntheseLigne cle="Depuis le">
        {{ autorisation().accordee_le | siraDate: 'jour' }}
      </div>
      @if (autorisation().retiree_le; as retrait) {
        <div siraSyntheseLigne cle="Retiré le">{{ retrait | siraDate: 'jour' }}</div>
      } @else if (autorisation().expire_le; as fin) {
        <div siraSyntheseLigne cle="Jusqu’au">{{ fin | siraDate: 'jour' }}</div>
      }
      <div siraSyntheseLigne cle="Utilisation">{{ utilisation() }}</div>
    </dl>
    <div carte-actions class="sira-groupe-boutons">
      @if (autorisation().etat === 'ACTIVE') {
        <ec-retrait [autorisation]="autorisation()" (retire)="retire.emit($event)" />
      }
      <a siraLien [routerLink]="['/acces', autorisation().id]">
        Voir le détail<span class="sira-masque">
          de l’accès de {{ autorisation().service.nom }}</span
        >
      </a>
    </div>
  </sira-carte>`,
})
export class CarteAcces {
  readonly autorisation = input.required<Autorisation>();
  readonly retire = output<Autorisation>();

  protected readonly informations = computed(() =>
    this.autorisation()
      .jeux.map((jeu) => jeu.libelle)
      .join(' · '),
  );
  protected readonly utilisation = computed(() => decrireUtilisation(this.autorisation()));
}
