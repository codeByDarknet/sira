import { Jeu, Verification } from './parcours';
import { DELAI_REPONSE_MS, progression } from './progression';

const JEUX: Jeu[] = [
  {
    code: 'etat-civil.majeur',
    libelle: 'Majorité',
    niveau: 'STATUT',
    registre: 'etat-civil',
    champs: [],
  },
  {
    code: 'casier.vierge',
    libelle: 'Casier vierge',
    niveau: 'STATUT',
    registre: 'casier',
    champs: [],
  },
  {
    code: 'diplomes.bac',
    libelle: 'Baccalauréat',
    niveau: 'STATUT',
    registre: 'diplomes',
    champs: [],
  },
];

function verification(jeu: string, etat: Verification['etat']): Verification {
  return {
    jeu,
    libelle: jeu,
    niveau: 'STATUT',
    registre: 'etat-civil',
    etat,
    valeur: etat === 'OBTENU' ? true : null,
    champs: null,
    piece: null,
    modifiee_le: '2026-11-17T09:00:00Z',
  };
}

describe('progression', () => {
  it('montre tout en cours tant que le dossier n’est pas lu', () => {
    const etapes = progression(JEUX, null, DELAI_REPONSE_MS * 10);
    expect(etapes.map((etape) => etape.texte)).toEqual(['En cours', 'En cours', 'En cours']);
    expect(etapes.every((etape) => etape.attendue)).toBe(true);
  });

  it('dit pour chaque information si elle est reçue, attendue ou non transmise', () => {
    const recues = [
      verification('etat-civil.majeur', 'OBTENU'),
      verification('casier.vierge', 'A_TRAITER'),
      verification('diplomes.bac', 'INCONNU'),
    ];
    expect(progression(JEUX, recues, 0).map((etape) => etape.texte)).toEqual([
      'Reçu',
      'En cours',
      'Non transmis',
    ]);

    const etapes = progression(JEUX, recues, DELAI_REPONSE_MS);
    expect(etapes[1]).toMatchObject({
      texte: 'En attente : le registre ne répond pas, nous réessayons',
      etat: 'attention',
      attendue: true,
    });
    expect(etapes[0].attendue).toBe(false);
    expect(etapes[2].attendue).toBe(false);
  });

  it('suit les vérifications du dossier, même si la démarche décrite a changé depuis', () => {
    const recues = [
      verification('etat-civil.majeur', 'OBTENU'),
      verification('autre.jeu', 'REUTILISE'),
    ];
    const etapes = progression(JEUX, recues, 0);
    expect(etapes.map((etape) => etape.jeu)).toEqual(['etat-civil.majeur', 'autre.jeu']);
    expect(etapes.every((etape) => !etape.attendue)).toBe(true);
  });
});
