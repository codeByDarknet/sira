import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Icone, NomIcone } from '../icone/icone';

/** Couleur de la pastille d'icône d'un indicateur. */
export type TonIndicateur = 'bleu' | 'vert' | 'orange' | 'rouge' | 'violet' | 'gris';

/**
 * Carte chiffrée d'un tableau de bord : icône dans une pastille colorée, libellé, grand nombre
 * et une ligne d'explication. Avec `lien`, toute la carte mène à l'écran concerné.
 *
 *     <sira-indicateur icone="send" ton="bleu" libelle="Demandes" [valeur]="12"
 *       note="reçues aujourd'hui" lien="/habilitations" />
 */
@Component({
  selector: 'sira-indicateur',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icone, NgTemplateOutlet, RouterLink],
  template: `<ng-template #corps>
      <span class="sira-indicateur__icone" aria-hidden="true">
        <sira-icone [nom]="icone()" />
      </span>
      <span class="sira-indicateur__textes">
        <span class="sira-indicateur__libelle">{{ libelle() }}</span>
        <span class="sira-indicateur__valeur">{{ valeur() }}</span>
        @if (note()) {
          <span class="sira-indicateur__note">{{ note() }}</span>
        }
      </span>
      @if (lien()) {
        <sira-icone class="sira-indicateur__fleche" nom="arrow-up-right" />
      }
    </ng-template>
    @if (lien(); as chemin) {
      <a class="sira-indicateur__cadre" [routerLink]="chemin">
        <ng-container [ngTemplateOutlet]="corps" />
      </a>
    } @else {
      <div class="sira-indicateur__cadre"><ng-container [ngTemplateOutlet]="corps" /></div>
    }`,
  host: { class: 'sira-indicateur', '[class]': "'sira-indicateur--' + ton()" },
})
export class Indicateur {
  readonly icone = input.required<NomIcone>();
  readonly libelle = input.required<string>();
  readonly valeur = input.required<string | number>();
  readonly note = input<string>();
  readonly ton = input<TonIndicateur>('bleu');
  readonly lien = input<string>();
}
