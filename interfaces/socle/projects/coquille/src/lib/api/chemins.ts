/**
 * Chemins du serveur d'application, toujours sur la même origine que l'interface :
 * nginx relaie `/api/` vers le serveur, préfixe compris.
 */
export const CHEMINS_API = {
  /** GET : 200 `{ nom, roles }` si une session existe, 401 sinon. Pose le cookie XSRF-TOKEN. */
  session: '/api/session',
  /** Navigation pleine page (pas un appel) : le serveur lance la connexion puis revient sur `retour`. */
  connexion: '/api/auth/connexion/sira',
  /** POST : ferme la session ; répond `{ url }` pour fermer aussi celle du fournisseur d'identité. */
  deconnexion: '/api/auth/deconnexion',
} as const;

/** Adresse de connexion qui ramène l'usager sur l'écran qu'il voulait ouvrir. */
export function adresseConnexion(retour: string): string {
  // Un chemin local uniquement : jamais d'adresse complète dans `retour`.
  const chemin = retour.startsWith('/') && !retour.startsWith('//') ? retour : '/';
  return `${CHEMINS_API.connexion}?retour=${encodeURIComponent(chemin)}`;
}
