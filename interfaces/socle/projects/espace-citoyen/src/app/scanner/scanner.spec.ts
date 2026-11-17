import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { fournirCoquille } from '@sira/coquille';
import { CAMERA, qualifierPanne } from './camera';
import { LECTEUR_QR } from './lecteur-qr';
import { Scanner } from './scanner';

const ID = '3f2a6c1e-9b7d-4e21-8a55-0c1d2e3f4a5b';

describe('Scanner', () => {
  let arrets: number;

  async function afficher(camera: () => Promise<MediaStream>, contenu: string | null = null) {
    arrets = 0;
    TestBed.configureTestingModule({
      providers: [
        fournirCoquille({ application: 'Essai', routes: [] }),
        provideHttpClientTesting(),
        { provide: CAMERA, useValue: camera },
        { provide: LECTEUR_QR, useValue: () => Promise.resolve(() => Promise.resolve(contenu)) },
      ],
    });
    // jsdom ne lit pas de vidéo : la lecture réussit sans rien afficher.
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    Object.defineProperty(HTMLMediaElement.prototype, 'srcObject', {
      configurable: true,
      writable: true,
      value: null,
    });
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const vue = TestBed.createComponent(Scanner);
    await vue.whenStable();
    await vi.waitFor(() => expect(vue.nativeElement.textContent).not.toContain('Ouverture'));
    await vue.whenStable();
    return { vue, page: vue.nativeElement as HTMLElement, navigation };
  }

  const flux = () =>
    Promise.resolve({ getTracks: () => [{ stop: () => arrets++ }] } as unknown as MediaStream);
  const panne = (nom: string) => () => Promise.reject(new DOMException('Essai', nom));

  afterEach(() => vi.restoreAllMocks());

  it('explique le refus de la caméra et propose la saisie du code', async () => {
    const { page } = await afficher(panne('NotAllowedError'));

    expect(page.textContent).toContain('La caméra n’est pas autorisée');
    expect(page.querySelector('#code-court')).not.toBeNull();
  });

  it('explique l’absence de caméra et propose la saisie du code', async () => {
    const { page } = await afficher(panne('NotFoundError'));

    expect(page.textContent).toContain('Aucune caméra');
    expect(page.querySelector('#code-court')).not.toBeNull();
  });

  it('ouvre la demande lue dans un QR code de ce site', async () => {
    const { navigation } = await afficher(flux, `${location.origin}/accord/${ID}`);
    expect(navigation).toHaveBeenCalledWith(['/accord', ID]);
  });

  it('ne suit pas un QR code qui mène ailleurs, et le dit', async () => {
    const { vue, page, navigation } = await afficher(flux, `https://exemple.org/accord/${ID}`);
    await vi.waitFor(() => expect(page.textContent).toContain('Ce QR code'));

    expect(navigation).not.toHaveBeenCalled();
    vue.destroy();
    expect(arrets).toBe(1);
  });
});

describe('qualifierPanne', () => {
  it('range les erreurs de la caméra en trois cas', () => {
    expect(qualifierPanne(new DOMException('', 'NotAllowedError'))).toBe('refusee');
    expect(qualifierPanne(new DOMException('', 'SecurityError'))).toBe('refusee');
    expect(qualifierPanne(new DOMException('', 'NotFoundError'))).toBe('absente');
    expect(qualifierPanne(new DOMException('', 'NotSupportedError'))).toBe('absente');
    expect(qualifierPanne(new DOMException('', 'NotReadableError'))).toBe('indisponible');
    expect(qualifierPanne(new Error('autre'))).toBe('indisponible');
  });
});
