import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  effect,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { Alerte, Bouton, Icone, Lien, Squelette } from '@sira/ui';
import type { PDFDocumentProxy } from 'pdfjs-dist';
import { Dialog } from 'primeng/dialog';

const ZOOM_MIN = 0.75;
const ZOOM_MAX = 2.5;
const PAS_ZOOM = 0.25;

/**
 * Fenêtre d'aperçu d'un document : un PDF est dessiné page par page (pdf.js) dans des canevas,
 * une image est affichée telle quelle. Les réponses de l'API ne peuvent pas être intégrées dans un
 * cadre (politique de contenu) : les dessiner évite d'assouplir cette règle.
 *
 *     <demo-apercu-pdf [titre]="titre" [adresse]="url" [image]="false" (ferme)="…" />
 */
@Component({
  selector: 'demo-apercu-pdf',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alerte, Bouton, Dialog, Icone, Lien, Squelette],
  template: `<p-dialog
    [visible]="true"
    (visibleChange)="$event || ferme.emit()"
    [modal]="true"
    [dismissableMask]="true"
    [draggable]="false"
    [resizable]="false"
    [header]="titre()"
    styleClass="sira-dialogue-pdf"
  >
    <div class="ps-apercu">
      @if (etat() === 'erreur') {
        <sira-alerte type="erreur" titre="Le document ne s’affiche pas">
          <p>Vous pouvez l’ouvrir dans un nouvel onglet.</p>
          <a siraLien icone="external-link" externe [href]="adresse()">Ouvrir le document</a>
        </sira-alerte>
      } @else if (image()) {
        <img
          class="ps-apercu__image"
          [src]="adresse()"
          [alt]="titre()"
          (error)="etat.set('erreur')"
        />
      } @else {
        <div class="ps-apercu__outils">
          <button
            siraBouton
            variante="secondaire"
            type="button"
            [disabled]="zoom() <= zoomMin"
            (click)="zoomer(-pasZoom)"
          >
            <sira-icone nom="zoom-out" /><span class="sira-masque">Réduire</span>
          </button>
          <span aria-live="polite">{{ pourcentage() }} %</span>
          <button
            siraBouton
            variante="secondaire"
            type="button"
            [disabled]="zoom() >= zoomMax"
            (click)="zoomer(pasZoom)"
          >
            <sira-icone nom="zoom-in" /><span class="sira-masque">Agrandir</span>
          </button>
          @if (pages() > 0) {
            <span>· {{ pages() }} page{{ pages() > 1 ? 's' : '' }}</span>
          }
          <span class="ps-apercu__espace"></span>
          <a siraLien icone="download" [href]="adresse()" download>Télécharger</a>
        </div>
        @if (etat() === 'chargement') {
          <sira-squelette [lignes]="6" libelle="Ouverture du document" />
        }
        <div #toile class="ps-apercu__pages" role="document" [attr.aria-label]="titre()"></div>
      }
    </div>
  </p-dialog>`,
})
export class ApercuPdf implements OnDestroy {
  readonly titre = input.required<string>();
  /** Adresse du document (même origine) : la session de l'agent l'autorise. */
  readonly adresse = input.required<string>();
  /** Vrai pour une image (PNG, JPEG) : pas de pdf.js. */
  readonly image = input(false);
  readonly ferme = output();

  protected readonly zoomMin = ZOOM_MIN;
  protected readonly zoomMax = ZOOM_MAX;
  protected readonly pasZoom = PAS_ZOOM;
  protected readonly etat = signal<'chargement' | 'pret' | 'erreur'>('chargement');
  protected readonly zoom = signal(1.25);
  protected readonly pages = signal(0);
  protected readonly pourcentage = computed(() => Math.round(this.zoom() * 100));

  private readonly toile = viewChild<ElementRef<HTMLDivElement>>('toile');
  private document: PDFDocumentProxy | null = null;

  constructor() {
    effect(() => {
      const adresse = this.adresse();
      if (!this.image()) {
        untracked(() => void this.ouvrir(adresse));
      }
    });
  }

  private async ouvrir(adresse: string): Promise<void> {
    this.etat.set('chargement');
    try {
      const pdfjs = await import('pdfjs-dist');
      pdfjs.GlobalWorkerOptions.workerSrc = 'assets/pdfjs/pdf.worker.min.mjs';
      // Ni évaluation de code, ni police injectée : compatible avec la politique de contenu.
      this.document = await pdfjs.getDocument({
        url: adresse,
        isEvalSupported: false,
        disableFontFace: true,
      }).promise;
      this.pages.set(this.document.numPages);
      await this.dessiner();
      this.etat.set('pret');
    } catch {
      this.etat.set('erreur');
    }
  }

  protected async zoomer(pas: number): Promise<void> {
    this.zoom.update((zoom) => Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, zoom + pas)));
    await this.dessiner();
  }

  ngOnDestroy(): void {
    void this.document?.destroy();
    this.document = null;
  }

  /** Dessine toutes les pages à l'échelle courante, nettes sur les écrans à haute densité. */
  private async dessiner(): Promise<void> {
    const document = this.document;
    // La fenêtre s'ouvre au tour suivant : on attend que le conteneur existe.
    await new Promise((fin) => requestAnimationFrame(fin));
    const conteneur = this.toile()?.nativeElement;
    if (!document || !conteneur) {
      return;
    }
    conteneur.replaceChildren();
    const densite = window.devicePixelRatio || 1;
    for (let numero = 1; numero <= document.numPages; numero++) {
      const page = await document.getPage(numero);
      const vue = page.getViewport({ scale: this.zoom() });
      const canevas = window.document.createElement('canvas');
      canevas.width = Math.floor(vue.width * densite);
      canevas.height = Math.floor(vue.height * densite);
      canevas.style.width = `${Math.floor(vue.width)}px`;
      canevas.setAttribute('aria-label', `Page ${numero} sur ${document.numPages}`);
      canevas.setAttribute('role', 'img');
      conteneur.append(canevas);
      await page.render({
        canvas: canevas,
        viewport: vue,
        transform: densite !== 1 ? [densite, 0, 0, densite, 0, 0] : undefined,
      }).promise;
    }
  }
}
