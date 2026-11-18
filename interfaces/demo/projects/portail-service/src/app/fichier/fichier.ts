import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { Alerte, Bouton, Icone, Lien, Squelette } from '@sira/ui';
import type { PDFDocumentProxy } from 'pdfjs-dist';
import { Dialog } from 'primeng/dialog';

const ZOOM_MIN = 0.75;
const ZOOM_MAX = 2.5;
const PAS_ZOOM = 0.25;

/**
 * Une pièce présentée comme une fiche : icône, nom, provenance et actions. « Prévisualiser »
 * ouvre la pièce dans une fenêtre, sans quitter la page : le PDF est dessiné page par page
 * (pdf.js) dans des canevas. La politique de contenu interdit d'intégrer la réponse de l'API dans
 * un cadre ; la dessiner évite de l'assouplir. D'autres actions (retirer, remplacer) se placent
 * dans `[fichier-actions]`.
 */
@Component({
  selector: 'ps-fichier',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Alerte, Bouton, Dialog, Icone, Lien, Squelette],
  template: `<sira-icone nom="file-text" />
    <div class="sira-fichier__corps">
      <p class="sira-fichier__nom">{{ nom() }}</p>
      @if (details()) {
        <p class="sira-fichier__details">{{ details() }}</p>
      }
      <div class="sira-fichier__actions">
        @if (apercu()) {
          <button siraBouton variante="secondaire" type="button" (click)="ouvrir()">
            <sira-icone nom="eye" />
            Prévisualiser<span class="sira-masque"> {{ nom() }}</span>
          </button>
        }
        @if (telechargement(); as adresse) {
          <a siraLien icone="download" [href]="adresse" download>
            Télécharger<span class="sira-masque"> {{ nom() }}</span>
          </a>
        }
        <ng-content select="[fichier-actions]" />
      </div>
    </div>

    <p-dialog
      [visible]="ouvert()"
      (visibleChange)="ouvert.set($event)"
      (onHide)="fermer()"
      [modal]="true"
      [dismissableMask]="true"
      [draggable]="false"
      [resizable]="false"
      [header]="nom()"
      styleClass="sira-dialogue-pdf"
    >
      <div class="ps-apercu">
        @if (etat() === 'erreur') {
          <sira-alerte type="erreur" titre="La pièce ne s’affiche pas">
            <p>Vous pouvez l’ouvrir dans un nouvel onglet.</p>
            @if (apercu(); as adresse) {
              <a siraLien icone="external-link" externe [href]="adresse">Ouvrir la pièce</a>
            }
          </sira-alerte>
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
          </div>
          @if (etat() === 'chargement') {
            <sira-squelette [lignes]="6" libelle="Ouverture de la pièce" />
          }
          <div #toile class="ps-apercu__pages" role="document" [attr.aria-label]="nom()"></div>
        }
      </div>
    </p-dialog>`,
  host: { class: 'sira-fichier' },
})
export class Fichier implements OnDestroy {
  /** Nom affiché : celui de la pièce, ou de l'information attendue. */
  readonly nom = input.required<string>();
  /** Provenance et taille, en une ligne. */
  readonly details = input<string | null>(null);
  readonly apercu = input<string | null>(null);
  readonly telechargement = input<string | null>(null);

  protected readonly zoomMin = ZOOM_MIN;
  protected readonly zoomMax = ZOOM_MAX;
  protected readonly pasZoom = PAS_ZOOM;
  protected readonly ouvert = signal(false);
  protected readonly etat = signal<'chargement' | 'pret' | 'erreur'>('chargement');
  protected readonly zoom = signal(1.25);
  protected readonly pages = signal(0);
  protected readonly pourcentage = computed(() => Math.round(this.zoom() * 100));

  private readonly toile = viewChild<ElementRef<HTMLDivElement>>('toile');
  private document: PDFDocumentProxy | null = null;

  protected async ouvrir(): Promise<void> {
    const adresse = this.apercu();
    if (!adresse) {
      return;
    }
    this.ouvert.set(true);
    this.etat.set('chargement');
    try {
      const pdfjs = await import('pdfjs-dist');
      pdfjs.GlobalWorkerOptions.workerSrc = 'assets/pdfjs/pdf.worker.min.mjs';
      // Ni évaluation de code, ni police injectée : compatible avec la politique de contenu.
      this.document = await pdfjs.getDocument({
        url: adresse,
        isEvalSupported: false,
        disableFontFace: true,
        withCredentials: false,
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

  protected fermer(): void {
    void this.document?.destroy();
    this.document = null;
    this.pages.set(0);
  }

  ngOnDestroy(): void {
    this.fermer();
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
