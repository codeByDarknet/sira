# Console

La console sert à ceux qui font vivre le socle : déclarer un membre ou un registre, rédiger les questions qu'on peut lui poser, préparer et approuver les habilitations des services, suivre l'état des registres, relire le journal et vérifier son intégrité, répondre aux signalements des citoyens. Elle est servie sur http://console.sira.localhost:8600.

Trois rôles s'y partagent le travail. L'instructeur prépare, l'autorité approuve, rejette ou suspend, l'auditeur lit tout et ne modifie rien. Une habilitation préparée par une personne ne peut pas être approuvée par la même : c'est le principe des quatre yeux, et c'est la passerelle qui le fait respecter. La connexion demande un mot de passe et un code TOTP, dans le royaume Keycloak `sira-socle`, distinct de celui des citoyens.

## Comment elle s'articule

Comme pour l'espace citoyen, la console est faite d'écrans Angular ([`interfaces/socle/projects/console`](../interfaces/socle/projects/console)) et d'un serveur, celui de ce dossier (module `sira-console`). Le serveur ouvre la session, garde les jetons hors du navigateur (cookie `SIRA_CONSOLE`) et relaie les appels des écrans vers `/v1/admin/**` de la [passerelle](../passerelle/README.md), en TLS mutuel. Le relais est une liste fermée : `RoutesConsole` énumère chaque route permise et le rôle qu'elle exige. Ce qui n'y figure pas n'atteint jamais la passerelle ; celle-ci refait néanmoins son propre contrôle, le seul qui fasse foi.

## Spécification et tests

Les routes relayées sont celles de la passerelle : elles sont décrites dans [`passerelle/openapi.yaml`](../passerelle/openapi.yaml), et dans [`journal/openapi.yaml`](../journal/openapi.yaml) pour le journal et les signalements.

```sh
./mvnw -q -ntp verify -pl console -am
```

Les tests utilisent une fausse passerelle. `AutreRoyaumeTest` vérifie qu'un jeton émis par un autre royaume n'ouvre pas de session. Les écrans se testent depuis `interfaces/socle`, avec `npx ng test console --watch=false`.

## Avant de modifier

Une route ajoutée à la passerelle n'est pas visible de la console tant qu'elle n'est pas dans `RoutesConsole`, avec le niveau d'accès qui lui convient. En cas de doute, choisissez le plus restrictif. Le fonctionnement commun à la console et à l'espace citoyen est décrit dans [docs/composants/serveurs-applications.md](../docs/composants/serveurs-applications.md).
