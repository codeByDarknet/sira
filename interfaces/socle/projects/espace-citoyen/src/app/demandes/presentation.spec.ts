import { champsEnClair } from './presentation';

describe('champsEnClair', () => {
  it('nomme les champs du registre en clair', () => {
    expect(champsEnClair('Informations transmises : nom, prenoms, date_naissance')).toBe(
      'Informations transmises : nom, prénoms, date de naissance',
    );
  });

  it('garde un champ inconnu, sans soulignés', () => {
    expect(champsEnClair('Informations transmises : numero_dossier')).toBe(
      'Informations transmises : numero dossier',
    );
  });

  it('laisse un texte sans liste tel quel', () => {
    expect(champsEnClair('Quelques informations')).toBe('Quelques informations');
    expect(champsEnClair(undefined)).toBeUndefined();
  });
});
