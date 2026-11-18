# Consentement et minimisation

Deux garde-fous, posés à deux endroits différents, pour qu'aucune information ne circule sans que la personne l'ait voulu, et jamais plus que ce dont le service a besoin.

Le premier est côté socle. Quand le service des concours ouvre une demande, rien ne part vers les registres tant que la candidate ne l'a pas acceptée depuis son espace. Elle peut y être conduite directement par le site du service, recopier un code court que le service lui affiche, ou retrouver la demande en se connectant. Un accord vaut pour une démarche et une durée fixée par celle-ci, et elle peut le retirer à tout moment.

Le second est chez l'administration qui détient le registre. Le connecteur posé devant le registre a sa propre politique locale, en YAML, et tout ce qu'elle ne liste pas est refusé, quoi que demande la passerelle. Il répond de préférence par oui ou par non (« majeur : oui ») plutôt que par la donnée elle-même (la date de naissance), puis signe sa réponse et la chiffre pour le seul service demandeur.

## Ce que contient ce dossier

`consentement/` (module `sira-consentement`) est une bibliothèque embarquée dans la [passerelle](../passerelle/README.md). On y trouve les accords et les retraits (`Accords`, `Autorisations`), les codes courts et de vérification (`Codes`), les pseudonymes qui remplacent l'identifiant unique auprès des services (`Citoyens`) et les vues réduites que le citoyen voit de chaque demande (`Vues`). Elle attend de la passerelle l'interface `Echanges` et fournit au journal `CitoyensJournal`.

`connecteur/` (module `sira-connecteur`) est une application à part entière, déployée une fois par registre. Elle lit la politique locale et le fichier de raccordement au démarrage, vérifie chaque requête signée de la passerelle, interroge le registre, applique les règles et renvoie une attestation. Les politiques des registres de démonstration sont dans [`registres/connecteurs/`](../registres/connecteurs), pas ici.

## Spécification et tests

[`openapi.yaml`](openapi.yaml) décrit les routes du citoyen (`/v1/citoyen/**`, servies par la passerelle) et la seule route du connecteur, `POST /v1/requetes`, que seule la passerelle peut appeler.

```sh
./mvnw -q -ntp verify -pl consentement-minimisation/consentement,consentement-minimisation/connecteur -am
```

Côté connecteur, `PolitiqueTest` et `RefusTest` montrent ce qui est refusé, et chaque réponse reçue pendant les tests est validée contre la spécification. Les refus de bout en bout du consentement (demande d'un autre citoyen, code faux, décision prise deux fois) sont joués dans `passerelle/serveur`, par `RefusTest`.

## Avant de modifier

Une politique locale se lit de façon stricte : une clé inconnue, un chemin en double ou un fichier vide empêchent le connecteur de démarrer, et c'est voulu. Si vous ajoutez un opérateur de règle, pensez à ce qu'on pourrait en déduire par questions successives ; c'est pour cela qu'une politique peut restreindre les opérateurs et les valeurs admis pour un champ (un seuil d'âge libre permettrait de retrouver une date de naissance). La signature et le chiffrement passent par les briques de [`commun/`](../commun/README.md), jamais par du code écrit ici.

Le format de la politique et du raccordement est détaillé dans [docs/composants/connecteur.md](../docs/composants/connecteur.md).
