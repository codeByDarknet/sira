import { Verification, compteRendu, libelleResultat, libelleType, nomDe } from './lignes';

const INTACT: Verification = {
  valide: true,
  lignes: 1284,
  dernier_hachage: 'a3f1',
  dernier_point_de_controle: { horodatage: '2026-11-16T08:00:00Z', ligne: 1280, jws: 'e30.e30.x' },
  premiere_rupture: null,
};

describe('compte rendu de la vérification du journal', () => {
  it('dit qu’un journal intact l’est, avec le nombre de lignes et le dernier point de contrôle', () => {
    const rendu = compteRendu(INTACT);
    expect(rendu.valide).toBe(true);
    expect(rendu.titre).toBe('Le journal est intact');
    expect(rendu.phrases[0]).toMatch(/^1\s284 lignes vérifiées\./);
    expect(rendu.phrases[1]).toContain('ligne n° 1280');
  });

  it('désigne la première rupture d’un journal altéré, sans jamais le dire intact', () => {
    const rendu = compteRendu({ ...INTACT, valide: false, premiere_rupture: 512 });
    expect(rendu.valide).toBe(false);
    expect(rendu.titre).toBe('Le journal a été altéré');
    expect(rendu.phrases[0]).toContain('512');
    expect(rendu.phrases.join(' ')).not.toContain('intact');
  });

  it('désigne la rupture par son numéro de ligne, comme la passerelle la renvoie', () => {
    const rendu = compteRendu({ ...INTACT, valide: false, premiere_rupture: 1277 });
    expect(rendu.phrases[0]).toMatch(/ligne n° 1\s277/);
  });

  it('le dit quand aucun point de contrôle n’existe encore', () => {
    const rendu = compteRendu({ ...INTACT, lignes: 1, dernier_point_de_controle: null });
    expect(rendu.phrases[0]).toMatch(/^1 ligne vérifiée\./);
    expect(rendu.phrases[1]).toBe('Aucun point de contrôle signé.');
  });
});

describe('libellés du journal', () => {
  it('affiche tel quel un type ou un résultat inconnu plutôt que de le masquer', () => {
    expect(libelleType('CONSULTATION')).toBe('Consultation d’un registre');
    expect(libelleType('NOUVEAU_TYPE')).toBe('NOUVEAU_TYPE');
    expect(libelleResultat('REFUSE')).toEqual({ texte: 'Refusé', etat: 'erreur' });
    expect(libelleResultat('AUTRE')).toEqual({ texte: 'AUTRE', etat: 'neutre' });
  });

  it('lit une référence donnée par son code ou par un objet', () => {
    expect(nomDe('etat-civil')).toBe('etat-civil');
    expect(nomDe({ code: 'etat-civil', nom: 'État civil' })).toBe('État civil');
    expect(nomDe(null)).toBe('');
  });
});
