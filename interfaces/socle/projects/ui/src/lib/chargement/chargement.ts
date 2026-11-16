import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Écran de chargement plein cadre : le logo de Sira et une barre qui avance. Affiché pendant la
 * lecture de la session et le départ vers la page de connexion, à la place d'un cadre vide.
 */
@Component({
  selector: 'sira-chargement',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="sira-chargement" role="status">
    <img
      class="sira-chargement__signe"
      src="assets/sira/marque/sira-logo.png"
      alt=""
      width="480"
      height="446"
    />
    <span class="sira-chargement__barre" aria-hidden="true"></span>
    <span class="sira-masque">{{ libelle() }}</span>
  </div>`,
})
export class Chargement {
  readonly libelle = input('Chargement en cours');
}
