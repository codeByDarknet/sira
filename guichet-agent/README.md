# Guichet agent

Ce dossier joue le rôle des administrations qui instruisent une démarche, et il n'est pas destiné à la production. Il montre à quoi ressemble un service une fois raccordé à Sira, des deux côtés du guichet.

Côté usager, Aïcha ouvre http://concours.sira.localhost:8600, saisit son identifiant unique dans le formulaire d'inscription, voit ce qui sera récupéré et auprès de quel registre, puis donne son accord dans son espace Sira. Sira vérifie que l'identifiant saisi est bien le sien : celui d'une autre personne mène à une demande introuvable, et le service ne reçoit rien. De retour sur le site du concours, elle suit la récupération information par information ; le formulaire revient prérempli et verrouillé. Elle n'ajoute que ce qu'aucun registre ne détient (son CV en PDF, un téléphone de contact), prévisualise ou télécharge chaque pièce, et dépose son dossier. Côté agent, `agent.concours@sira.example` se connecte au même site et instruit le dossier avec ce qui a été reçu : quatre réponses par oui ou par non, son nom et ses prénoms, ce qu'elle a déclaré elle-même, mais ni sa date de naissance ni son identifiant unique.

Trois démarches sont proposées, chacune sur son propre site, `http://<code>.sira.localhost:8600` : l'inscription à un concours direct (`concours`), la demande de bourse d'études (`bourses`) et l'immatriculation d'un commerçant au registre du commerce (`entreprises`).

## Comment il s'articule

Il n'y a qu'un seul serveur (`serveur/`, module `sira-demo-service`), lancé une fois par démarche avec un profil Spring : `application-concours.yaml`, `application-bourses.yaml` ou `application-entreprises.yaml`. Il parle à la [passerelle](../passerelle/README.md) uniquement à travers le kit `ClientSira`, en TLS mutuel, exactement comme le ferait le service d'un ministère : c'est le meilleur exemple de raccordement que contienne le dépôt. Les écrans sont dans `interfaces/demo/projects/portail-service`, et les agents se connectent par le royaume Keycloak de démonstration `demo-agents`.

Ajouter une quatrième démarche ne demande pas de code : un fichier `application-<code>.yaml`, la démarche et son habilitation dans `passerelle/amorcage-demo/`, puis le service, son portail et son adresse dans `deploiement/`. `ProfilsTest` vérifie que chaque profil s'accorde avec l'amorçage.

## Spécification et tests

[`openapi.yaml`](openapi.yaml) décrit les routes que le portail appelle : le parcours du candidat (`/api/parcours/**`, session anonyme), l'instruction par l'agent (`/api/agent/dossiers/**`) et la description de la démarche (`/api/demarche`).

```sh
./mvnw -q -ntp verify -pl guichet-agent/serveur -am
```

Les tests utilisent une fausse passerelle en TLS mutuel, et Docker pour la base. Chaque réponse reçue est validée contre la spécification.

## Avant de modifier

Le service ne doit jamais avoir besoin de plus que ce que la démarche déclare. S'il vous manque une information, la bonne réponse est une nouvelle question dans l'habilitation, approuvée dans la console, pas un champ de plus à demander à l'usager. Le formulaire propre à chaque démarche (`sira.formulaire.declarations`) ne sert qu'à ce qu'aucun registre ne détient. Le détail est dans [docs/composants/demo-services.md](../docs/composants/demo-services.md).
