/** Une personne fictive et la boîte où arrivent ses messages. */
export interface Citoyen {
  identifiant: string;
  nom: string;
  adresse: string;
}

const citoyen = (identifiant: string, nom: string): Citoyen => ({
  identifiant,
  nom,
  adresse: `${identifiant}@telephone.sira.example`,
});

/**
 * Les citoyens de `identite/demo/sira-citoyens-users-0.json`. La liste est recopiée ici parce que
 * l'image de l'écran ne contient pas ce fichier ; `tests/e2e` vérifie que les deux concordent.
 */
export const CITOYENS: readonly Citoyen[] = [
  citoyen('990048217350', 'Wendkuni Aïcha KABORÉ'),
  citoyen('990017734062', 'Bakary Désiré SANOU'),
  citoyen('990063390218', 'Pingdwendé Rasmata ZONGO'),
  citoyen('990029915477', 'Dieudonné Yirbar SOMÉ'),
  citoyen('990085502641', 'Mariam Sita COULIBALY'),
  citoyen('990051176839', 'Hamidou Boureima DICKO'),
];
