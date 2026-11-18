# Passerelle

La passerelle est le seul point de passage entre les services qui instruisent une démarche et les registres qui détiennent l'information. Quand le service des concours veut savoir si une candidate est majeure, il ne contacte pas l'état civil : il ouvre une demande auprès de la passerelle, qui attend l'accord de la personne, interroge le connecteur du registre et remet au service une réponse qu'elle n'a pas pu lire.

Elle tient aussi le référentiel du socle : les membres et leurs certificats, les registres, les questions qu'on peut leur poser, les démarches et les habilitations qui disent quel service a le droit de poser quelles questions. C'est la console qui l'administre.

## Ce que contient ce dossier

`serveur/` est l'application Spring Boot (module `sira-passerelle`). Elle embarque dans un même processus les bibliothèques [`journal`](../journal/README.md), [`consentement`](../consentement-minimisation/README.md) et [`resilience`](../resilience/README.md), si bien qu'un accord, l'échange qui en découle et la ligne de journal qui le trace s'écrivent dans une seule transaction. Le schéma de base (Flyway) est ici aussi.

`kit/` (module `sira-kit`) est la bibliothèque qu'un service ajoute pour se raccorder. Sa classe `ClientSira` ouvre une demande, suit son état et déchiffre les attestations reçues. Les services simulés de [`guichet-agent/`](../guichet-agent/README.md) s'en servent exactement comme le ferait un vrai service.

`amorcage-demo/` n'appartient pas au socle : c'est le référentiel chargé au premier démarrage de la démonstration (membres, registres, questions, trois démarches et leurs habilitations approuvées).

## Spécification et tests

La spécification OpenAPI 3.1 est [`openapi.yaml`](openapi.yaml). Elle couvre les routes des services (`/v1/demandes`, `/v1/demarches/{code}`) et celles de l'administration (`/v1/admin/**`) ; les routes du citoyen, du journal et de l'état des registres sont décrites dans les dossiers des modules qui les fournissent.

```sh
./mvnw -q -ntp verify -pl passerelle/serveur,passerelle/kit -am
```

Docker doit tourner : les tests démarrent PostgreSQL avec Testcontainers. `ContratOpenApiTest` compare les routes Spring à la spécification, et chaque réponse reçue pendant les tests est validée contre elle au passage. `RefusTest` rassemble ce qui doit être refusé ; `JournalTest` et `ResilienceTest` éprouvent les modules embarqués avec une vraie base.

## Avant de modifier

Les modules embarqués ne dépendent jamais de la passerelle : c'est elle qui leur fournit ce dont ils ont besoin, par les interfaces `Echanges` (pour le consentement) et `Reprise` (pour la résilience). Gardez ce sens de dépendance. Toute route ajoutée doit figurer dans `openapi.yaml`, sinon `ContratOpenApiTest` échoue, et venir avec son test de refus. Le transport vers les connecteurs est direct (mTLS), derrière l'interface `Transport`.

Pour aller plus loin : [docs/composants/passerelle.md](../docs/composants/passerelle.md) et le [contrat d'architecture](../docs/architecture/CONTRAT.md).
