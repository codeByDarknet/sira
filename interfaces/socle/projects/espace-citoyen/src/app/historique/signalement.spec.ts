import { MOTIF_MAXIMUM } from './journal';
import { verifierMotif } from './signalement';

describe('verifierMotif', () => {
  it('admet un message d’une longueur raisonnable', () => {
    expect(verifierMotif('Je n’ai jamais fait cette démarche.')).toBeNull();
  });

  it('refuse un message vide, trop court ou trop long', () => {
    expect(verifierMotif('          ')).toContain('10 caractères au moins');
    expect(verifierMotif('Non')).toContain('10 caractères au moins');
    expect(verifierMotif('a'.repeat(MOTIF_MAXIMUM + 1))).toContain('500 caractères au plus');
  });
});
