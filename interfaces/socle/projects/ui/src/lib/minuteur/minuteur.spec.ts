import { phraseEcheance } from './minuteur';

describe('phraseEcheance', () => {
  const maintenant = new Date(2026, 10, 16, 14, 6, 10);

  it('donne le délai en minutes et l’heure limite', () => {
    const echeance = new Date(2026, 10, 16, 14, 10, 0);
    expect(phraseEcheance('Ce code', echeance, maintenant)).toBe(
      'Ce code expire dans 4 minutes, à 14\u00a0h\u00a010.',
    );
  });

  it('ne décompte pas les secondes', () => {
    const echeance = new Date(2026, 10, 16, 14, 6, 40);
    expect(phraseEcheance('Ce code', echeance, maintenant)).toContain('moins d’une minute');
  });

  it('donne la date au-delà d’une heure', () => {
    const echeance = new Date(2026, 11, 1, 9, 5, 0);
    expect(phraseEcheance('Cette demande', echeance, maintenant)).toBe(
      'Cette demande expire le 1er décembre 2026 à 9\u00a0h\u00a005.',
    );
  });

  it('dit quand l’échéance est passée', () => {
    const echeance = new Date(2026, 10, 16, 14, 0, 0);
    expect(phraseEcheance('Cette demande', echeance, maintenant)).toBe(
      'Cette demande a expiré à 14\u00a0h\u00a000.',
    );
  });
});
