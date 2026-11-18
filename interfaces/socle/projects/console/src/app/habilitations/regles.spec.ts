import { Jeu } from '../jeux/jeux';
import { EtatHabilitation, Habilitation } from './habilitations';
import { Acteur, gestesPermis, parRegistre, questionsARevoir } from './regles';

const INSTRUCTEUR: Acteur = { nom: 'Issa SAWADOGO', ecriture: true, decision: false };
const AUTORITE: Acteur = { nom: 'Mariam KABORÉ', ecriture: false, decision: true };
const AUDITEUR: Acteur = { nom: 'Paul ZONGO', ecriture: false, decision: false };

function habilitation(etat: EtatHabilitation, instruitePar = INSTRUCTEUR.nom): Habilitation {
  return {
    id: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
    service: 'concours',
    demarche: 'concours-direct',
    jeux: ['etat-civil.majeur'],
    etat,
    justification: 'Le concours est réservé aux personnes majeures.',
    instruite_par: instruitePar,
    decidee_par: null,
    decidee_le: null,
  };
}

function jeu(code: string, niveau: Jeu['niveau']): Jeu {
  const registre = code.split('.')[0];
  return { code, registre, niveau, libelle: code, validite: 'P90D', actif: true };
}

describe('gestes permis sur une habilitation', () => {
  it('laisse l’instructeur modifier et soumettre un brouillon, sans décider', () => {
    const gestes = gestesPermis(habilitation('BROUILLON'), INSTRUCTEUR);
    expect(gestes).toMatchObject({ modifier: true, soumettre: true, decisions: [] });
  });

  it('ne propose à l’instructeur aucune décision sur un dossier soumis ou en vigueur', () => {
    for (const etat of ['SOUMISE', 'APPROUVEE'] as const) {
      const gestes = gestesPermis(habilitation(etat), INSTRUCTEUR);
      expect(gestes.decisions).toEqual([]);
    }
  });

  it('laisse l’autorité approuver ou rejeter un dossier soumis par quelqu’un d’autre', () => {
    const gestes = gestesPermis(habilitation('SOUMISE'), AUTORITE);
    expect(gestes).toEqual({
      modifier: false,
      soumettre: false,
      decisions: ['approbation', 'rejet'],
      explication: null,
    });
  });

  it('laisse l’autorité suspendre une habilitation en vigueur, et rien d’autre', () => {
    expect(gestesPermis(habilitation('APPROUVEE'), AUTORITE).decisions).toEqual(['suspension']);
  });

  it('refuse à l’autorité de modifier ou soumettre un brouillon', () => {
    const gestes = gestesPermis(habilitation('BROUILLON'), AUTORITE);
    expect(gestes).toMatchObject({ modifier: false, soumettre: false, decisions: [] });
  });

  it('refuse de décider à qui a préparé le dossier, même avec les deux rôles', () => {
    const lesDeuxRoles: Acteur = { nom: 'Issa SAWADOGO', ecriture: true, decision: true };
    for (const etat of ['SOUMISE', 'APPROUVEE'] as const) {
      const gestes = gestesPermis(habilitation(etat), lesDeuxRoles);
      expect(gestes.decisions).toEqual([]);
      expect(gestes.explication).toContain('une autre personne');
    }
  });

  it('ne propose aucun geste à l’auditeur, quel que soit l’état', () => {
    for (const etat of ['BROUILLON', 'SOUMISE', 'APPROUVEE', 'REJETEE', 'SUSPENDUE'] as const) {
      const gestes = gestesPermis(habilitation(etat), AUDITEUR);
      expect(gestes).toMatchObject({ modifier: false, soumettre: false, decisions: [] });
    }
  });

  it('ne rouvre pas un dossier rejeté ou suspendu, pour aucun rôle', () => {
    for (const acteur of [INSTRUCTEUR, AUTORITE]) {
      for (const etat of ['REJETEE', 'SUSPENDUE'] as const) {
        const gestes = gestesPermis(habilitation(etat), acteur);
        expect(gestes).toMatchObject({ modifier: false, soumettre: false, decisions: [] });
      }
    }
  });
});

describe('rappel de minimisation', () => {
  const PUBLIEES = [
    jeu('etat-civil.majeur', 'STATUT'),
    jeu('etat-civil.date-naissance', 'CHAMPS'),
    jeu('etat-civil.extrait', 'DOCUMENT'),
    jeu('diplomes.liste', 'CHAMPS'),
  ];

  it('signale l’information demandée quand le registre propose un « oui ou non »', () => {
    const aRevoir = questionsARevoir(['etat-civil.date-naissance', 'etat-civil.extrait'], PUBLIEES);
    expect(aRevoir.map((question) => question.code)).toEqual([
      'etat-civil.date-naissance',
      'etat-civil.extrait',
    ]);
  });

  it('ne signale ni un « oui ou non », ni un registre qui n’en propose pas', () => {
    expect(questionsARevoir(['etat-civil.majeur', 'diplomes.liste'], PUBLIEES)).toEqual([]);
  });

  it('regroupe les questions par registre', () => {
    expect(parRegistre(PUBLIEES).map((groupe) => [groupe.registre, groupe.jeux.length])).toEqual([
      ['diplomes', 1],
      ['etat-civil', 3],
    ]);
  });
});
