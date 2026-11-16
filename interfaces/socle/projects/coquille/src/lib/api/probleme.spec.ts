import { HttpErrorResponse } from '@angular/common/http';
import { adresseConnexion } from './chemins';
import { lireProbleme } from './probleme';

describe('lireProbleme', () => {
  it('reprend le détail et les erreurs par champ d’un refus', () => {
    const erreur = new HttpErrorResponse({
      status: 422,
      error: {
        detail: 'La saisie est incomplète.',
        erreurs: [{ champ: 'nom', message: 'Saisissez le nom.' }],
      },
    });
    expect(lireProbleme(erreur)).toEqual({
      statut: 422,
      message: 'La saisie est incomplète.',
      champs: { nom: 'Saisissez le nom.' },
    });
  });

  it('ne montre jamais le texte d’une panne du serveur', () => {
    const erreur = new HttpErrorResponse({
      status: 500,
      error: { detail: 'NullPointerException' },
    });
    expect(lireProbleme(erreur).message).not.toContain('NullPointerException');
  });
});

describe('adresseConnexion', () => {
  it('n’accepte qu’un chemin local comme adresse de retour', () => {
    expect(adresseConnexion('/acces')).toBe('/api/auth/connexion/sira?retour=%2Facces');
    expect(adresseConnexion('//exemple.org')).toBe('/api/auth/connexion/sira?retour=%2F');
    expect(adresseConnexion('https://exemple.org')).toBe('/api/auth/connexion/sira?retour=%2F');
  });
});
