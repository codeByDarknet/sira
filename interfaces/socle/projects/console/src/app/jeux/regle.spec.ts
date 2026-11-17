import { Operateur } from './jeux';
import {
  OPERATEURS,
  SAISIE_VIDE,
  SaisieJeu,
  construireJeu,
  etapes,
  phraseContenu,
  phraseRegle,
  verifierEtape,
  versSaisie,
} from './regle';

const MAJEUR: SaisieJeu = {
  ...SAISIE_VIDE,
  registre: 'etat-civil',
  niveau: 'STATUT',
  champ: 'date_naissance',
  operateur: 'AGE_AU_MOINS',
  valeur: '18',
  libelle: 'Vous êtes majeur',
  suffixe: 'majeur',
  validite_jours: '90',
};

describe('assistant de règle', () => {
  it('propose les neuf opérateurs du contrat, chacun avec un libellé et un exemple', () => {
    const attendus: Operateur[] = [
      'EGAL',
      'DIFFERENT',
      'AU_MOINS',
      'AU_PLUS',
      'AGE_AU_MOINS',
      'AGE_AU_PLUS',
      'EXISTE',
      'AUCUN',
      'ENCORE_VALIDE',
    ];
    expect(OPERATEURS.map((operateur) => operateur.code).sort()).toEqual([...attendus].sort());
    for (const operateur of OPERATEURS) {
      expect(operateur.libelle).not.toContain('_');
      expect(operateur.exemple.length).toBeGreaterThan(10);
    }
  });

  it('dit chaque règle en une phrase, sans vocabulaire technique', () => {
    expect(phraseRegle({ champ: 'date_naissance', operateur: 'AGE_AU_MOINS', valeur: 18 })).toBe(
      'la personne a au moins 18 ans, d’après «\u00a0date_naissance\u00a0»',
    );
    expect(phraseRegle({ champ: 'condamnations', operateur: 'AUCUN' })).toBe(
      'rien n’est inscrit dans «\u00a0condamnations\u00a0»',
    );
    for (const { code } of OPERATEURS) {
      expect(phraseRegle({ champ: 'x', operateur: code, valeur: 1 })).not.toContain(code);
    }
  });

  it('nomme la troisième étape d’après la forme de réponse choisie', () => {
    expect(etapes('')).toHaveLength(5);
    expect(etapes('STATUT')[2]).toBe('Règle');
    expect(etapes('CHAMPS')[2]).toBe('Informations transmises');
    expect(etapes('DOCUMENT')[2]).toBe('Document');
  });

  it('ne laisse pas passer une étape incomplète', () => {
    expect(verifierEtape(1, SAISIE_VIDE)).toHaveProperty('registre');
    expect(verifierEtape(2, { ...SAISIE_VIDE, registre: 'etat-civil' })).toHaveProperty('niveau');
    expect(verifierEtape(3, { ...MAJEUR, champ: '', operateur: '' })).toEqual({
      'regle.champ': expect.any(String),
      'regle.operateur': expect.any(String),
    });
    expect(
      verifierEtape(4, { ...MAJEUR, libelle: '', suffixe: 'Majeur !', validite_jours: '0' }),
    ).toEqual({
      libelle: expect.any(String),
      code: expect.any(String),
      validite: expect.any(String),
    });
  });

  it('refuse un nom d’information qui ne peut pas être un champ de registre', () => {
    for (const champ of ['date naissance', 'Date', '1champ', "x'; DROP TABLE", '$.nom']) {
      expect(verifierEtape(3, { ...MAJEUR, champ })).toHaveProperty(['regle.champ']);
    }
  });

  it('exige la valeur que l’opérateur attend, et seulement celle-là', () => {
    expect(verifierEtape(3, { ...MAJEUR, valeur: 'dix-huit' })).toHaveProperty(['regle.valeur']);
    expect(verifierEtape(3, { ...MAJEUR, valeur: '18,5' })).toHaveProperty(['regle.valeur']);
    expect(verifierEtape(3, { ...MAJEUR, operateur: 'AU_MOINS', valeur: '12,5' })).toEqual({});
    expect(verifierEtape(3, { ...MAJEUR, operateur: 'EGAL', valeur: ' ' })).toHaveProperty([
      'regle.valeur',
    ]);
    expect(verifierEtape(3, { ...MAJEUR, operateur: 'EXISTE', valeur: '' })).toEqual({});
  });

  it('laisse passer les quatre étapes d’une question complète', () => {
    for (const etape of [1, 2, 3, 4]) {
      expect(verifierEtape(etape, MAJEUR)).toEqual({});
    }
  });

  it('construit la question au format du contrat', () => {
    expect(construireJeu(MAJEUR)).toEqual({
      code: 'etat-civil.majeur',
      registre: 'etat-civil',
      niveau: 'STATUT',
      libelle: 'Vous êtes majeur',
      validite: 'P90D',
      regle: { champ: 'date_naissance', operateur: 'AGE_AU_MOINS', valeur: 18 },
      actif: true,
    });
  });

  it('n’envoie pas de valeur pour un opérateur qui n’en attend pas', () => {
    const jeu = construireJeu({ ...MAJEUR, operateur: 'AUCUN', valeur: 'reste de saisie' });
    expect(jeu.regle).toEqual({ champ: 'date_naissance', operateur: 'AUCUN' });
  });

  it('n’envoie que le contenu du niveau choisi : une règle abandonnée ne part pas', () => {
    const champs = construireJeu({ ...MAJEUR, niveau: 'CHAMPS', champs: 'nom, prenoms' });
    expect(champs.champs).toEqual(['nom', 'prenoms']);
    expect(champs).not.toHaveProperty('regle');
    expect(champs).not.toHaveProperty('document');

    const document = construireJeu({
      ...MAJEUR,
      niveau: 'DOCUMENT',
      document: 'extrait-naissance',
    });
    expect(document.document).toBe('extrait-naissance');
    expect(document).not.toHaveProperty('regle');
  });

  it('remet une question existante dans l’assistant sans la déformer', () => {
    const jeu = construireJeu(MAJEUR);
    expect(construireJeu(versSaisie(jeu))).toEqual(jeu);
  });

  it('résume ce qu’une question laisse sortir du registre', () => {
    expect(phraseContenu(construireJeu(MAJEUR))).toBe(
      'Réponse «\u00a0oui\u00a0» si la personne a au moins 18 ans, d’après «\u00a0date_naissance\u00a0». Sinon «\u00a0non\u00a0».',
    );
  });
});
