# Interfaces

Tous les écrans de Sira, écrits en Angular. Ils sont rangés dans deux espaces de travail, et la frontière entre les deux est la même que dans le reste du dépôt.

`socle/` contient ce qu'un déploiement réel servirait : l'espace citoyen (`espace-citoyen`) et la console d'administration (`console`). Il contient aussi les deux bibliothèques sur lesquelles reposent tous les écrans. `ui` est la couche visuelle : la feuille de style et les composants, sans appel réseau ni règle métier. `coquille` est ce que toute application partage : l'accès à `/api`, la session, les gardes de route, les titres de page et les pages d'erreur. La `vitrine`, enfin, présente chaque composant et un écran d'exemple complet ; c'est le meilleur point de départ pour écrire un nouvel écran.

`demo/` contient les écrans de la simulation : le portail des services (`portail-service`, une même application pour les trois démarches, côté usager et côté agent), le back-office des registres simulés (`registre-admin`) et le téléphone simulé (`telephone`), qui affiche les codes de connexion. Cet espace travaille sur une copie des bibliothèques du socle, refaite avant chaque démarrage et chaque construction : une bibliothèque se modifie toujours dans `socle/projects/`, jamais dans `demo/bibliotheques/`.

Aucun écran ne parle directement à la passerelle. Chaque application déployée est servie par nginx, qui relaie `/api/` vers son serveur d'application : [`espace-usager/`](../espace-usager/README.md) pour l'espace citoyen, [`console/`](../console/README.md) pour la console, [`guichet-agent/`](../guichet-agent/README.md) et [`registres/`](../registres/README.md) pour le portail et le back-office de démonstration. Le téléphone simulé n'a pas de serveur : il lit la messagerie de démonstration par un relais en lecture seule. Les appels de l'espace citoyen et du portail sont décrits dans les `openapi.yaml` de leur dossier, ceux de la console dans celui de la [passerelle](../passerelle/openapi.yaml). Les pages de connexion, elles, appartiennent à Keycloak : leur thème est dans [`identite/theme`](../identite/theme).

Pour travailler sur les écrans, il faut Node.js 24 :

```sh
cd interfaces/socle            # ou interfaces/demo
npm ci
npm run demarrer -- vitrine    # serveur de développement
npm run lint                   # ESLint, Prettier, et feuille de style à jour
npm run tester                 # tests de ui et coquille (socle seulement)
npm run construire             # construction de production
```

Les tests d'une application se lancent par son nom, par exemple `npx ng test espace-citoyen --watch=false` depuis `interfaces/socle`.

Une règle mérite d'être connue avant toute modification : la politique de sécurité du contenu n'autorise aucun script en ligne, et aucun style en ligne sauf ceux qui portent le nonce de la requête. Ce nonce, tiré au hasard par nginx à chaque chargement de la page d'entrée (`deploiement/nginx/en-tetes-page.conf`), sert uniquement aux feuilles de thème que PrimeNG pose à l'exécution (`coquille/src/lib/apparence/theme.ts`). Les composants Sira n'ont donc toujours pas de styles propres et on n'écrit jamais d'attribut `style` ; toute la mise en forme vit dans `socle/projects/ui/styles` (`habillage.css` pour l'apparence, `tailwind.css` pour les utilitaires Tailwind), et `npm run css` réassemble la feuille après une retouche.

Les écrans s'appuient sur PrimeNG 21 (préréglage Aura recoloré en bleu) : `siraBouton` est un bouton PrimeNG, `siraSaisie` un champ PrimeNG, `sira-alerte` un message, `sira-etiquette` une étiquette, `sira-squelette` un squelette PrimeNG. Le portail des démarches affiche les pièces PDF dans une fenêtre PrimeNG, dessinées par pdf.js. Le reste des conventions (appels à l'API, formulaires, écriture des textes) est dans [docs/developpement/interfaces.md](../docs/developpement/interfaces.md).
