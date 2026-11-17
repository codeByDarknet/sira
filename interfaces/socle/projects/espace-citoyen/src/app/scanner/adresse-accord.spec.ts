import { lireAdresseAccord } from './adresse-accord';

const ORIGINE = 'https://citoyen.sira.test';
const ID = '3f2a6c1e-9b7d-4e21-8a55-0c1d2e3f4a5b';

describe('lireAdresseAccord', () => {
  it('admet l’adresse d’un écran d’accord de ce site', () => {
    expect(lireAdresseAccord(`${ORIGINE}/accord/${ID}`, ORIGINE)).toBe(ID);
    expect(lireAdresseAccord(`  ${ORIGINE}/accord/${ID.toUpperCase()}\n`, ORIGINE)).toBe(ID);
  });

  it.each([
    ['un autre site', `https://citoyen.sira.test.exemple.org/accord/${ID}`],
    ['un autre protocole', `http://citoyen.sira.test/accord/${ID}`],
    ['un autre port', `https://citoyen.sira.test:8443/accord/${ID}`],
    ['un nom d’usager devant un autre site', `https://citoyen.sira.test@exemple.org/accord/${ID}`],
    ['un nom d’usager devant ce site', `https://usager:secret@citoyen.sira.test/accord/${ID}`],
    ['une autre page du site', `${ORIGINE}/acces/${ID}`],
    ['un chemin plus long', `${ORIGINE}/accord/${ID}/decision`],
    ['un chemin qui remonte', `${ORIGINE}/accord/../acces/${ID}`],
    ['un identifiant mal formé', `${ORIGINE}/accord/42`],
    ['des paramètres', `${ORIGINE}/accord/${ID}?retour=https://exemple.org`],
    ['un fragment', `${ORIGINE}/accord/${ID}#suite`],
    ['un script', 'javascript:alert(1)'],
    ['une adresse relative', `/accord/${ID}`],
    ['un texte quelconque', 'Bonjour'],
    ['un contenu vide', ''],
    ['un contenu démesuré', `${ORIGINE}/accord/${ID}${' '.repeat(1)}${'a'.repeat(300)}`],
  ])('refuse %s', (_cas, contenu) => {
    expect(lireAdresseAccord(contenu, ORIGINE)).toBeNull();
  });
});
