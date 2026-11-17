import { TestBed } from '@angular/core/testing';
import { IdentifiantSaisi, identifiantValide, normaliserIdentifiant } from './identifiant';

describe('identifiant unique', () => {
  it('admet 12 chiffres, espaces retirés, et rien d’autre', () => {
    expect(identifiantValide(normaliserIdentifiant('9900 4821 7350'))).toBe(true);
    expect(identifiantValide('99004821735')).toBe(false);
    expect(identifiantValide('9900482173501')).toBe(false);
    expect(identifiantValide('99004821735a')).toBe(false);
    expect(identifiantValide('')).toBe(false);
  });

  it('n’est gardé qu’en mémoire, bien formé, jusqu’à ce qu’on l’oublie', () => {
    const memoire = TestBed.inject(IdentifiantSaisi);
    memoire.retenir('abc');
    expect(memoire.identifiant()).toBeNull();
    memoire.retenir('990048217350');
    expect(memoire.identifiant()).toBe('990048217350');
    memoire.oublier();
    expect(memoire.identifiant()).toBeNull();
  });
});
