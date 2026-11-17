import { normaliserCode } from './saisie-code';

describe('normaliserCode', () => {
  it('met le code sous sa forme d’envoi', () => {
    expect(normaliserCode('klpm 4821')).toBe('KLPM-4821');
    expect(normaliserCode(' KLPM-4821 ')).toBe('KLPM-4821');
  });

  it.each(['', 'KLPM', 'KLPM-48211', 'KLPM_4821', '../../etc', 'klpm-48é1'])(
    'refuse « %s »',
    (saisie) => {
      expect(normaliserCode(saisie)).toBeNull();
    },
  );
});
