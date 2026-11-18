# Déploiement

Tout ce qu'il faut pour faire tourner Sira avec Docker Compose sur un poste. On ne lance presque jamais ces fichiers à la main : `scripts/sira demarrer` et `scripts/sira demo` choisissent les bons, créent `.env` à partir de [`.env.example`](.env.example) s'il manque, attendent que tout soit sain et affichent les adresses.

Les fichiers Compose s'empilent. [`compose.yaml`](compose.yaml) décrit le socle seul : l'autorité de certification, la base, Keycloak, la passerelle, l'espace citoyen, la console et le proxy d'entrée. [`compose.demo.yaml`](compose.demo.yaml) y ajoute la simulation : les registres et leurs connecteurs, les services des trois démarches, le téléphone simulé et sa messagerie, chaque administration fictive dans son propre réseau privé. La même séparation vaut pour les fichiers lus par le conteneur `pki` : `membres.conf` et `secrets.conf` pour le socle, `membres.demo.conf` et `secrets.demo.conf` pour la démonstration.

Les autres dossiers contiennent la configuration des conteneurs :

- `proxy/` : le proxy d'entrée, seul port publié (`127.0.0.1:8600` par défaut), qui répartit les requêtes selon le nom d'hôte, et la configuration d'`acces-identite`, la porte en TLS mutuel placée devant Keycloak ;
- `nginx/` : la configuration commune des conteneurs qui servent les écrans Angular ;
- `docker/` : les deux recettes d'image, une pour toutes les applications Java, une pour toutes les interfaces ;
- `bases/` : la création des rôles et des bases PostgreSQL, rejouable par `scripts/sira bases` ;

Les conteneurs permanents sont durcis de la même manière (système de fichiers en lecture seule, mémoire et journaux bornés), et les images sont épinglées par empreinte `sha256`. Aucun secret n'est écrit dans ces fichiers : le conteneur `pki` les génère au premier démarrage et les range dans des volumes, et chaque conteneur ne monte que les siens.

Ce dossier n'a ni spécification OpenAPI ni tests unitaires. Il est vérifié sur la pile qui tourne : `scripts/sira verifier` contrôle que ce qui doit rester fermé l'est (ports, réseaux, routes d'administration), et `scripts/sira tester` joue les parcours de bout en bout.

Avant de modifier, sachez qu'un service ajouté à la démonstration ne doit jamais apparaître dans `compose.yaml` : le socle seul doit démarrer sans rien savoir de la simulation. Le mode d'emploi complet (variables, réseaux, ajout d'un membre, d'un service Java ou d'un écran) est dans [docs/developpement/infrastructure.md](../docs/developpement/infrastructure.md).
