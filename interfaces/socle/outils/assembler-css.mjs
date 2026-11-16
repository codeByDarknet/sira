/*
 * Assemble la couche CSS de @sira/ui en un fichier autonome : projects/ui/styles/sira.css.
 * Ce fichier sert hors d'Angular (thème de l'écran de connexion, page d'un service tiers).
 * Avec --verifier, la commande échoue si le fichier versionné n'est plus à jour.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const styles = join(dirname(fileURLToPath(import.meta.url)), '..', 'projects', 'ui', 'styles');
const parties = [
  'polices.css',
  'jetons.css',
  'themes.css',
  'base.css',
  'composants.css',
  'habillage.css',
  'interface.css',
  'ajustements.css',
  'vitrine.css',
];
const cible = join(styles, 'sira.css');

const contenu =
  '/* Fichier produit par « npm run css » à partir de ' +
  parties.join(', ') +
  '. Ne pas le modifier à la main. */\n' +
  parties
    .map((nom) => `\n/* ===== ${nom} ===== */\n${readFileSync(join(styles, nom), 'utf8')}`)
    .join('');

if (process.argv.includes('--verifier')) {
  let actuel = '';
  try {
    actuel = readFileSync(cible, 'utf8');
  } catch {
    // Fichier absent : traité comme périmé.
  }
  if (actuel !== contenu) {
    console.error('projects/ui/styles/sira.css n’est plus à jour : lancez « npm run css ».');
    process.exit(1);
  }
} else {
  writeFileSync(cible, contenu);
  console.log(`sira.css assemblé (${Math.round(contenu.length / 1024)} ko).`);
}
