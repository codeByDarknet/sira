import { tailleFichier } from './libelles';

describe('tailleFichier', () => {
  it('écrit la taille en unités lisibles', () => {
    expect(tailleFichier(840)).toBe('840 octets');
    expect(tailleFichier(120 * 1024)).toBe('120 Ko');
    expect(tailleFichier(1.8 * 1024 * 1024)).toBe('1,8 Mo');
  });
});
