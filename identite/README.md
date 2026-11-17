# Identité

Sira s'adresse à trois populations qui ne doivent jamais se croiser : les citoyens, les personnes qui administrent le socle et, dans la démonstration, les agents des administrations simulées. Chacune a son royaume Keycloak, avec ses propres comptes, ses propres règles de connexion et ses propres jetons, si bien qu'une session ouverte dans l'un ne vaut rien dans les autres.

Un citoyen se connecte avec son identifiant unique, puis un code à six chiffres envoyé sur son téléphone, ou avec une clé d'accès. Un administrateur de la console donne un mot de passe et un code TOTP. Ce dossier construit l'image Keycloak qui sert tout cela.

## Ce que contient ce dossier

- `royaumes/` : les royaumes du socle, `sira-citoyens` et `sira-socle`, importés au démarrage. Ils ne contiennent aucun compte.
- `extension/` : l'étape de connexion des citoyens (`IdentifiantAuthenticator`, `CodeUsageUnique`). Le code vaut cinq minutes et cinq essais, et un même identifiant en reçoit au plus dix par heure. La page ne laisse jamais deviner si un identifiant existe.
- `theme/` : le thème des pages de connexion, en français et en anglais.
- `demarrer.sh` : le lanceur, qui place les secrets dans l'environnement de Keycloak au dernier moment.
- `demo/` : le royaume `demo-agents` et les comptes fictifs. Ces fichiers ne servent qu'à la démonstration, et le lanceur refuse de démarrer s'il en trouve un hors du mode démonstration.

L'extension est compilée dans l'image, contre les bibliothèques de la version exacte de Keycloak visée (voir le `Dockerfile`). Keycloak ne parle qu'en HTTP sur un réseau fermé ; le TLS mutuel avec les serveurs d'application est assuré devant lui par le conteneur `acces-identite`, décrit dans [`deploiement/`](../deploiement/README.md).

## Tests

Ce dossier n'a pas de module Maven ni de spécification OpenAPI : ses routes sont celles de Keycloak. Son comportement est vérifié sur la pile qui tourne, par `scripts/sira verifier` (routes d'administration fermées, enregistrement de clients refusé) et par les tests de bout en bout de `tests/e2e`, qui jouent les connexions et leurs refus.

## Avant de modifier

Après une modification de l'extension ou du thème, il faut reconstruire l'image, ce que fait `scripts/sira demarrer` ou `scripts/sira demo`. Un royaume se modifie dans son fichier JSON, pas dans l'interface d'administration de Keycloak, qui n'est d'ailleurs pas exposée. Pour envoyer de vrais SMS, c'est `CodeUsageUnique.transmettre` qu'il faut remplacer. La marche à suivre pour ajouter un royaume, un client ou un compte est dans [docs/developpement/infrastructure.md](../docs/developpement/infrastructure.md).
