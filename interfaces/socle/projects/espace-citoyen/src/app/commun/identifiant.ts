import { CanMatchFn } from '@angular/router';

const UUID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';

/** Identifiant d'objet de l'API : un UUID, rien d'autre. */
export const EST_IDENTIFIANT = new RegExp(`^${UUID}$`, 'i');

/** Chemin d'un écran d'accord : `/accord/<identifiant>`. */
export const CHEMIN_ACCORD = new RegExp(`^/accord/(${UUID})$`, 'i');

/** Route `…/:id` : une adresse dont l'identifiant n'en est pas un mène à « page introuvable ». */
export const gardeIdentifiant: CanMatchFn = (_route, segments) =>
  EST_IDENTIFIANT.test(segments.at(-1)?.path ?? '');
