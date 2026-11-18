import { expect, request, test } from '@playwright/test';
import { ADRESSES } from '../../outils/adresses';

/**
 * Le socle seul (`scripts/sira demarrer`) : rien de la démonstration n'est servi. L'appel vise la
 * boucle locale avec le nom d'hôte en en-tête, comme la lecture de la messagerie.
 */
async function statut(adresse: string, chemin = '/'): Promise<number> {
  const cible = new URL(adresse);
  const contexte = await request.newContext({
    baseURL: `http://127.0.0.1:${cible.port || '80'}`,
    extraHTTPHeaders: { Host: cible.host },
  });
  try {
    return (await contexte.get(chemin, { maxRedirects: 0 })).status();
  } finally {
    await contexte.dispose();
  }
}

test.describe('Socle seul', () => {
  test('l’espace citoyen et la console sont servis', async () => {
    expect(await statut(ADRESSES.citoyen)).toBe(200);
    expect(await statut(ADRESSES.console)).toBe(200);
  });

  test('le téléphone simulé et sa messagerie n’existent pas', async () => {
    expect(await statut(ADRESSES.telephone)).toBe(404);
    expect(await statut(ADRESSES.telephone, '/messagerie/search?query=sira')).toBe(404);
  });

  test('aucun service ni registre simulé n’est servi', async () => {
    for (const ecran of [
      ADRESSES.concours,
      ADRESSES.bourses,
      ADRESSES.entreprises,
      ADRESSES.etatCivil,
      ADRESSES.nationalite,
      ADRESSES.casier,
      ADRESSES.diplomes,
    ]) {
      expect(await statut(ecran), ecran).toBe(404);
    }
  });

  test('le royaume des agents de démonstration n’existe pas', async () => {
    expect(
      await statut(ADRESSES.identite, '/realms/demo-agents/.well-known/openid-configuration'),
    ).toBe(404);
  });
});
