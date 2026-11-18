import { DOCUMENT, InjectionToken, inject } from '@angular/core';

/** Lit le QR code visible dans l'image courante de la vidéo ; `null` s'il n'y en a pas. */
export type LecteurQr = (video: HTMLVideoElement) => Promise<string | null>;

interface DetecteurNatif {
  detect(image: HTMLVideoElement): Promise<{ rawValue: string }[]>;
}
interface ClasseDetecteur {
  new (options: { formats: string[] }): DetecteurNatif;
  getSupportedFormats(): Promise<string[]>;
}

/**
 * Prépare la lecture des QR codes : le détecteur du navigateur (`BarcodeDetector`) quand il
 * existe, sinon la bibliothèque jsQR, chargée seulement dans ce cas.
 */
export const LECTEUR_QR = new InjectionToken<() => Promise<LecteurQr>>('LECTEUR_QR', {
  providedIn: 'root',
  factory: () => {
    const document = inject(DOCUMENT);
    return async () => {
      const natif = (document.defaultView as { BarcodeDetector?: ClasseDetecteur } | null)
        ?.BarcodeDetector;
      if (natif && (await natif.getSupportedFormats()).includes('qr_code')) {
        const detecteur = new natif({ formats: ['qr_code'] });
        return async (video) => (await detecteur.detect(video))[0]?.rawValue ?? null;
      }
      return lecteurDeRepli(document);
    };
  },
});

async function lecteurDeRepli(document: Document): Promise<LecteurQr> {
  const { default: jsQR } = await import('jsqr');
  const toile = document.createElement('canvas');
  const dessin = toile.getContext('2d', { willReadFrequently: true });
  return (video) => {
    if (!dessin || video.videoWidth === 0) {
      return Promise.resolve(null);
    }
    toile.width = video.videoWidth;
    toile.height = video.videoHeight;
    dessin.drawImage(video, 0, 0);
    const image = dessin.getImageData(0, 0, toile.width, toile.height);
    const code = jsQR(image.data, image.width, image.height, { inversionAttempts: 'dontInvert' });
    return Promise.resolve(code?.data ?? null);
  };
}
