import { Etape } from '@sira/ui';

/** Les quatre temps de la démarche, rappelés en haut de chaque écran du parcours. */
export const ETAPES: readonly Etape[] = [
  { titre: 'Identification', detail: 'Votre identifiant unique' },
  { titre: 'Autorisation', detail: 'Votre accord dans Sira' },
  { titre: 'Récupération', detail: 'Les administrations répondent' },
  { titre: 'Dépôt', detail: 'Vous vérifiez, puis vous envoyez' },
];
