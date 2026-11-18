import { Verification } from '../parcours/parcours';
import { TAILLE_MAX_PIECE, provenance, refusPiece } from './dossier';

function verification(etat: Verification['etat']): Verification {
  return {
    jeu: 'casier.vierge',
    libelle: 'Casier judiciaire vierge',
    niveau: 'STATUT',
    registre: 'casier',
    etat,
    valeur: null,
    champs: null,
    piece: null,
    modifiee_le: '2026-11-17T09:00:00Z',
  };
}

describe('formulaire du dossier', () => {
  it('dit d’où vient chaque information du registre', () => {
    expect(provenance(verification('OBTENU'))).toBe('Vérifié par Casier judiciaire');
    expect(provenance(verification('A_TRAITER'))).toBe(
      'En attente de Casier judiciaire : l’information arrivera toute seule.',
    );
    expect(provenance(verification('REFUSE'))).toContain('Non transmis par Casier judiciaire');
  });

  it('n’envoie qu’un PDF non vide de 2 Mo au plus', () => {
    const pdf = (taille: number, nom = 'cv.pdf', type = 'application/pdf') =>
      new File([new Uint8Array(taille)], nom, { type });
    expect(refusPiece(pdf(1000))).toBeNull();
    expect(refusPiece(pdf(1000, 'cv.PDF', ''))).toBeNull();
    expect(refusPiece(pdf(TAILLE_MAX_PIECE))).toBeNull();
    expect(refusPiece(pdf(TAILLE_MAX_PIECE + 1))).toBe('Le fichier dépasse 2 Mo.');
    expect(refusPiece(pdf(0))).toBe('Le fichier est vide.');
    expect(refusPiece(pdf(1000, 'cv.html', 'text/html'))).toBe('Choisissez un fichier PDF.');
  });
});
