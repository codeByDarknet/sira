import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import qrcode from 'qrcode-generator';

/** Marge blanche autour du code, en modules : la norme en demande quatre. */
const MARGE = 4;

/**
 * QR code dessiné en SVG. Noir sur blanc quel que soit le thème : c'est ce que les appareils
 * photo lisent le mieux.
 */
@Component({
  selector: 'ps-qr-code',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg
    xmlns="http://www.w3.org/2000/svg"
    width="208"
    height="208"
    role="img"
    shape-rendering="crispEdges"
    [attr.viewBox]="cadre()"
    [attr.aria-label]="libelle()"
  >
    <rect width="100%" height="100%" fill="#ffffff" />
    <path fill="#000000" [attr.d]="trace()" />
  </svg>`,
})
export class QrCode {
  readonly contenu = input.required<string>();
  readonly libelle = input.required<string>();

  private readonly code = computed(() => {
    const code = qrcode(0, 'M');
    code.addData(this.contenu());
    code.make();
    return code;
  });

  protected readonly cadre = computed(() => {
    const cote = this.code().getModuleCount() + 2 * MARGE;
    return `0 0 ${cote} ${cote}`;
  });

  protected readonly trace = computed(() => {
    const code = this.code();
    const taille = code.getModuleCount();
    let trace = '';
    for (let ligne = 0; ligne < taille; ligne++) {
      for (let colonne = 0; colonne < taille; colonne++) {
        if (code.isDark(ligne, colonne)) {
          trace += `M${colonne + MARGE} ${ligne + MARGE}h1v1h-1z`;
        }
      }
    }
    return trace;
  });
}
