import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { Alerte, Bouton, Icone } from '@sira/ui';
import { SaisieCode } from '../demandes/saisie-code';
import { lireAdresseAccord } from './adresse-accord';
import { CAMERA, PanneCamera, qualifierPanne } from './camera';
import { LECTEUR_QR, LecteurQr } from './lecteur-qr';

/** Pause entre deux lectures de l'image : assez court pour l'usager, léger pour le téléphone. */
const PAUSE_LECTURE = 250;

/**
 * Scanner du QR code affiché par un service. Sans caméra, ou si l'usager la refuse,
 * l'écran l'explique et propose la saisie du code court.
 */
@Component({
  selector: 'ec-scanner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alerte, Bouton, Icone, SaisieCode],
  templateUrl: './scanner.html',
})
export class Scanner {
  private readonly ouvrirCamera = inject(CAMERA);
  private readonly preparerLecteur = inject(LECTEUR_QR);
  private readonly routeur = inject(Router);
  private readonly origine = inject(DOCUMENT).location.origin;
  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');

  protected readonly etat = signal<'ouverture' | 'active' | PanneCamera>('ouverture');
  /** Un QR code a été lu, mais ce n'est pas une demande de ce site. */
  protected readonly codeEtranger = signal(false);

  private flux?: MediaStream;
  private ferme = false;

  constructor() {
    afterNextRender(() => void this.demarrer());
    inject(DestroyRef).onDestroy(() => {
      this.ferme = true;
      this.couper();
    });
  }

  protected async demarrer(): Promise<void> {
    this.etat.set('ouverture');
    try {
      const lire = await this.preparerLecteur();
      this.flux = await this.ouvrirCamera();
      if (this.ferme) {
        // L'usager a quitté l'écran pendant la demande d'autorisation.
        this.couper();
        return;
      }
      const video = this.video().nativeElement;
      video.srcObject = this.flux;
      await video.play();
      this.etat.set('active');
      void this.chercher(lire, video);
    } catch (erreur) {
      this.couper();
      this.etat.set(qualifierPanne(erreur));
    }
  }

  private async chercher(lire: LecteurQr, video: HTMLVideoElement): Promise<void> {
    while (!this.ferme) {
      const contenu = await lire(video).catch(() => null);
      if (contenu !== null) {
        const demande = lireAdresseAccord(contenu, this.origine);
        if (demande) {
          void this.routeur.navigate(['/accord', demande]);
          return;
        }
        this.codeEtranger.set(true);
      }
      await new Promise((suite) => setTimeout(suite, PAUSE_LECTURE));
    }
  }

  /** Éteint la caméra : le voyant de l'appareil ne reste pas allumé hors de cet écran. */
  private couper(): void {
    this.flux?.getTracks().forEach((piste) => piste.stop());
    this.flux = undefined;
  }
}
