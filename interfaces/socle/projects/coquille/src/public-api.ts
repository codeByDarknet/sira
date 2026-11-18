/*
 * @sira/coquille : ce que toutes les applications branchent de la même façon
 * (accès au serveur, session, gardes de route, titres, pages d'erreur).
 */
export * from './lib/api/api';
export * from './lib/api/chemins';
export * from './lib/api/intercepteur';
export * from './lib/api/probleme';
export * from './lib/api/redirection';
export * from './lib/fournir';
export * from './lib/navigation/titre';
export * from './lib/pages/page-profil';
export * from './lib/pages/pages-erreur';
export * from './lib/session/gardes';
export * from './lib/session/session';
