// Copie les bibliothèques du socle (@sira/ui, @sira/coquille) dans `bibliotheques/`.
// Angular n'accepte ni ressource ni feuille de style hors de l'espace de travail, et un
// import fait depuis l'extérieur chercherait Angular dans un autre `node_modules`. La copie
// n'est jamais versionnée : la source reste dans interfaces/socle/projects.
import { cpSync, existsSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const racine = join(dirname(fileURLToPath(import.meta.url)), '..');
const source = join(racine, '..', 'socle', 'projects');
const destination = join(racine, 'bibliotheques');

if (!existsSync(join(source, 'ui'))) {
  console.error(`Bibliothèques introuvables dans ${source}.`);
  process.exit(1);
}
rmSync(destination, { recursive: true, force: true });
for (const bibliotheque of ['ui', 'coquille']) {
  cpSync(join(source, bibliotheque), join(destination, bibliotheque), {
    recursive: true,
    filter: (chemin) => !chemin.endsWith('.spec.ts'),
  });
}
console.log('Bibliothèques du socle copiées dans bibliotheques/.');
