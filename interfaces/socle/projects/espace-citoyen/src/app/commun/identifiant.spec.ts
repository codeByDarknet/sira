import { TestBed } from '@angular/core/testing';
import { Route, UrlSegment } from '@angular/router';
import { gardeIdentifiant } from './identifiant';

const admet = (chemin: string) =>
  TestBed.runInInjectionContext(() =>
    gardeIdentifiant({} as Route, [new UrlSegment('accord', {}), new UrlSegment(chemin, {})]),
  );

describe('gardeIdentifiant', () => {
  it('n’ouvre la route qu’à un identifiant bien formé', () => {
    expect(admet('3f2a6c1e-9b7d-4e21-8a55-0c1d2e3f4a5b')).toBe(true);
    expect(admet('42')).toBe(false);
    expect(admet('3f2a6c1e-9b7d-4e21-8a55-0c1d2e3f4a5b%2F..')).toBe(false);
    expect(admet('rattachement')).toBe(false);
  });
});
