# Registres simulés

Rien dans ce dossier n'est destiné à la production. Pour essayer Sira, il faut des registres à interroger ; ceux des administrations ne sont pas à notre portée, alors ce dossier en fournit quatre, remplis de personnes fictives : état civil, nationalité, casier judiciaire et diplômes. Une vraie administration garde son propre registre et n'y ajoute qu'un [connecteur](../consentement-minimisation/README.md).

Ces registres sont volontairement ordinaires. Ils ne savent rien de Sira, de ses signatures ni de ses accords : ils rendent la fiche d'une personne à qui la demande sur leur réseau, comme le ferait une application existante. Tout ce qui protège la personne se passe dans le connecteur placé devant eux.

## Ce que contient ce dossier

`serveur/` (module `sira-demo-registre`) est un registre générique, lancé une fois par registre. Ce qui le distingue d'un registre à l'autre est dans `donnees/<code>/` : `registre.yaml` décrit ses champs et les documents qu'il délivre, `personnes.json` contient les personnes fictives. Chaque registre a aussi un back-office pour ses agents (par exemple http://casier.sira.localhost:8600), où l'on consulte et corrige une fiche, voit les appels reçus et, surtout, provoque une panne : le registre peut devenir lent, répondre par des erreurs ou ne plus répondre du tout. C'est ainsi qu'on voit la [résilience](../resilience/README.md) à l'œuvre.

`connecteurs/<code>.yaml` est la politique locale du connecteur posé devant chaque registre, et `raccordements/<code>.yaml` lui dit comment lire une fiche (adresse, chemin, authentification, correspondance des champs). Ce sont ces deux fichiers qu'une administration écrirait pour son propre registre.

`etranger/` contient un registre d'un tout autre genre, écrit en Node, avec des champs en anglais et une clé d'API, ainsi que sa politique et son raccordement. Il sert à montrer qu'on raccorde un registre existant sans le modifier : `scripts/sira etranger` le démarre avec son connecteur, puis rejoue tout le raccordement depuis la console.

## Spécification et tests

[`openapi.yaml`](openapi.yaml) est le contrat que nous proposons pour un registre d'état civil, de nationalité ou de casier (`/v1/<code>/personnes/{identifiant}`). Un registre qui ne le suit pas reste raccordable : c'est le rôle du fichier de raccordement.

```sh
./mvnw -q -ntp verify -pl registres/serveur -am
```

Docker est nécessaire pour PostgreSQL. Ces tests vérifient aussi que les données, les politiques des connecteurs et l'amorçage de la passerelle restent d'accord entre eux.

## Avant de modifier

Les données sont fictives et doivent le rester : n'y mettez jamais l'identité d'une personne réelle. Pour ajouter des citoyens fictifs à une pile qui tourne, `scripts/sira citoyens importer <fichier>` les crée à la fois dans les registres et dans Keycloak. Si vous ajoutez un champ à un registre, il ne sortira que si la politique de son connecteur le liste. Le détail est dans [docs/composants/demo-registres.md](../docs/composants/demo-registres.md).
