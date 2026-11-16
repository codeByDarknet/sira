# Contribuer à Sira

Merci de vous intéresser à Sira. Que vous corrigiez une faute dans la documentation, signaliez une anomalie ou proposiez de raccorder un nouveau registre, voici ce qu'il faut savoir pour que votre contribution trouve sa place facilement.

## Préparer son poste

Pour lancer la pile, Docker avec le greffon Compose suffit (voir [Essayer en cinq minutes](README.md#essayer-en-cinq-minutes)). Pour développer, ajoutez Java 21 pour les modules Java, et Node.js 24 pour les écrans et les tests de bout en bout. Les tests Java démarrent PostgreSQL avec Testcontainers : Docker doit donc tourner pendant `./mvnw verify`.

Avant de toucher à plusieurs modules à la fois, lisez le [contrat d'architecture](docs/architecture/CONTRAT.md). Il fixe ce que les modules attendent les uns des autres, et une règle qui en concerne plusieurs y est écrite avant d'être codée. Pour suivre une demande dans le code de bout en bout, [comprendre.md](docs/comprendre.md) est le meilleur point de départ.

## Ce à quoi nous tenons

Le code, les commentaires, les messages et la documentation sont en français, avec des noms qui disent ce que fait la chose.

Le socle ne dépend jamais de la simulation. Si votre changement ne sert qu'à la démonstration, il va dans `registres/`, `guichet-agent/`, `passerelle/amorcage-demo/`, `identite/demo/`, `interfaces/demo/` ou `deploiement/compose.demo.yaml`. De même, la passerelle dépend de ses modules (`journal`, `consentement`, `resilience`), jamais l'inverse.

Aucun secret, certificat ni clé n'entre dans le dépôt, même pour un essai. Tout est généré au premier démarrage à partir de `deploiement/secrets*.conf` et `deploiement/membres*.conf`.

Pas de cryptographie maison : signatures, chiffrement, pseudonymes et vérification des certificats passent par les briques de [`commun/`](commun/README.md).

Une route ou une règle de sécurité nouvelle arrive avec le test qui montre ce qu'elle refuse, et sa description dans le `openapi.yaml` du module ; les tests de contrat échouent sinon.

Les dépendances sont épinglées à une version exacte, et les images Docker de base par empreinte `sha256`.

## Vérifier avant de proposer

```sh
./mvnw spotless:apply    # remet le code Java en forme
./mvnw verify            # tests Java et contrôle du format
scripts/sira demo        # puis, sur la pile qui tourne :
scripts/sira verifier
scripts/sira tester
```

Si vous avez touché aux écrans, lancez aussi `npm run lint` et les tests dans l'espace Angular concerné ; les commandes sont dans [interfaces.md](docs/developpement/interfaces.md). Les tests qui se connectent consomment des codes de connexion, limités à dix par heure pour un même citoyen fictif : en cas de blocage, attendez ou repartez d'une pile neuve avec `scripts/sira reinitialiser`.

## Proposer une modification

Une branche par sujet, des commits courts dont le message dit ce qui change et pourquoi. Mettez à jour dans le même changement la documentation qui en parle : le README du dossier, `openapi.yaml`, ou la page de `docs/` concernée. Si votre proposition change un comportement visible du citoyen, décrivez-le simplement dans la demande de fusion.

Pour une faille de sécurité, n'ouvrez pas de ticket public : suivez [SECURITY.md](SECURITY.md).

En contribuant, vous acceptez que votre contribution soit publiée sous la licence du projet, l'[EUPL-1.2](LICENSE).
