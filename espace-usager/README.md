# Espace usager

C'est ici que le citoyen garde la main. Depuis http://citoyen.sira.localhost:8600, il accepte ou refuse les demandes qui le concernent, voit quel service a consulté quel registre et à quel moment, et retire un accès qu'il avait donné. Une demande peut aussi l'y attendre sans qu'il soit passé par le site du service : il la retrouve en se connectant, ou en saisissant le code court qu'un service lui affiche.

## Comment il s'articule

L'espace est fait de deux parties. Les écrans sont une application Angular, rangée avec les autres interfaces du socle dans [`interfaces/socle/projects/espace-citoyen`](../interfaces/socle/projects/espace-citoyen). Ce dossier contient leur serveur (`serveur/`, module `sira-espace-citoyen`).

Ce serveur fait deux choses. Il ouvre la session du citoyen auprès de Keycloak, dans le royaume `sira-citoyens`, et garde les jetons pour lui : le navigateur ne reçoit qu'un cookie de session. Puis il relaie les appels des écrans (`/api/**`) vers les routes `/v1/citoyen/**` de la [passerelle](../passerelle/README.md), en TLS mutuel et avec le jeton du citoyen. Ce relais est fermé : seules les routes listées dans `RoutesCitoyen` passent, tout le reste est refusé avant d'atteindre la passerelle. Le serveur ne décide de rien d'autre ; les accords, le journal et les contrôles d'accès vivent dans la passerelle.

## Spécification et tests

[`openapi.yaml`](openapi.yaml) décrit ce que voient les écrans : la session (cookie `SIRA_CITOYEN`, jeton CSRF sur les écritures) et les routes relayées, dont les schémas reprennent ceux de la passerelle.

```sh
./mvnw -q -ntp verify -pl espace-usager/serveur -am
```

`ContratOpenApiTest` compare au code la liste des routes relayées et celles de la session, et valide des réponses réelles contre la spécification. Les tests des écrans se lancent depuis `interfaces/socle`, avec `npx ng test espace-citoyen --watch=false`.

## Avant de modifier

Ajouter une route à l'espace citoyen, c'est l'ajouter à la fois dans la passerelle, dans `RoutesCitoyen` et dans `openapi.yaml` ; le test de contrat signale tout oubli. Le téléphone simulé qui reçoit les codes de connexion n'est pas ici : il fait partie de la démonstration, dans `interfaces/demo/projects/telephone`. Les écrans suivent les règles communes décrites dans [docs/developpement/interfaces.md](../docs/developpement/interfaces.md), et le fonctionnement du serveur est détaillé dans [docs/composants/serveurs-applications.md](../docs/composants/serveurs-applications.md).
