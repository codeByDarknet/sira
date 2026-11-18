import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Compte, ouvrirSession, page, textes } from '../essais/banc';
import { EcranSignalements } from './signalements';
import { Signalement, verifierReponse } from './signalements.service';

const ID = '0b9f0f3e-5b0e-4a51-8a37-0d2f5a8f3c11';
const A_TRAITER: Signalement = {
  id: ID,
  ligne: {
    id: '5c1d7b9a-1111-4222-8333-444455556666',
    horodatage: '2026-11-15T09:00:00Z',
    type: 'CONSULTATION',
    resultat: 'REUSSI',
    service: { code: 'concours', nom: 'Concours directs 2026' },
  },
  motif: 'Je n’ai jamais fait de démarche auprès de ce service.',
  cree_le: '2026-11-15T10:00:00Z',
  reponse: null,
  traite_par: null,
  traite_le: null,
};

describe('EcranSignalements', () => {
  let serveur: HttpTestingController;

  afterEach(() => serveur.verify());

  async function afficher(compte: Compte) {
    serveur = await ouvrirSession(compte);
    const vue = TestBed.createComponent(EcranSignalements);
    // Tant qu'une lecture est en cours l'écran n'est pas stable : on avance d'un rendu.
    TestBed.tick();
    const lecture = serveur.expectOne((requete) => requete.url === '/api/signalements');
    expect(lecture.request.params.get('etat')).toBe('A_TRAITER');
    lecture.flush(page([A_TRAITER]));
    await vue.whenStable();
    const ecran = vue.nativeElement as HTMLElement;
    return { vue, ecran };
  }

  it.each(['instructeur', 'autorite'] as const)('propose de répondre à l’%s', async (compte) => {
    const { ecran } = await afficher(compte);
    expect(textes(ecran, 'table button, [role="table"] button')).toHaveLength(1);
  });

  it('ne propose pas de répondre à l’auditeur', async () => {
    const { ecran } = await afficher('auditeur');
    expect(ecran.querySelectorAll('button[aria-label^="Répondre"]')).toHaveLength(0);
    expect(ecran.textContent).toContain('Seuls un instructeur ou l’autorité y répondent.');
    expect(ecran.textContent).toContain('En attente de réponse');
  });

  it('n’envoie pas une réponse vide, puis envoie la réponse saisie', async () => {
    const { vue, ecran } = await afficher('instructeur');
    const boutons = () => Array.from(ecran.querySelectorAll('button'));
    boutons()
      .find((bouton) => bouton.getAttribute('aria-label')?.startsWith('Répondre'))
      ?.click();
    await vue.whenStable();
    const envoyer = boutons().find((bouton) => bouton.textContent?.includes('Envoyer'));
    envoyer?.click();
    await vue.whenStable();
    serveur.expectNone(`/api/signalements/${ID}/traitement`);

    const zone = ecran.querySelector('textarea') as HTMLTextAreaElement;
    zone.value = 'Accès vérifié : il correspond à votre inscription du 3 novembre.';
    zone.dispatchEvent(new Event('input'));
    envoyer?.click();
    const envoi = serveur.expectOne(`/api/signalements/${ID}/traitement`);
    expect(envoi.request.body).toEqual({
      reponse: 'Accès vérifié : il correspond à votre inscription du 3 novembre.',
    });
    envoi.flush({ ...A_TRAITER, reponse: 'x', traite_le: '2026-11-16T09:00:00Z' });
    TestBed.tick();
    serveur.expectOne((requete) => requete.url === '/api/signalements').flush(page([]));
    await vue.whenStable();
    expect(ecran.textContent).toContain('Réponse enregistrée.');
  });
});

describe('verifierReponse', () => {
  it('refuse une réponse trop courte ou trop longue', () => {
    expect(verifierReponse('  ok  ')).toBeTruthy();
    expect(verifierReponse('a'.repeat(1001))).toBeTruthy();
    expect(verifierReponse('Accès vérifié et légitime.')).toBeNull();
  });
});
