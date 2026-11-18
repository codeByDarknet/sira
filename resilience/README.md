# Résilience

Un registre peut tomber : coupure de courant, réseau, maintenance. Sans précaution, la candidate qui s'inscrit au concours à ce moment-là verrait sa démarche échouer, et un registre lent finirait par ralentir tous les autres. Ce module évite les deux.

Quand le casier judiciaire ne répond pas, la passerelle enregistre quand même le dossier, avec les réponses déjà reçues des autres registres, et garde la question en attente. Elle la repose d'elle-même, à intervalles, jusqu'à ce que le casier revienne ; le dossier se complète alors sans que personne n'ait rien à refaire.

## Comment il s'articule

Le module `sira-resilience` est une bibliothèque embarquée dans la [passerelle](../passerelle/README.md). Il apporte deux choses.

`Disjoncteurs` donne à chaque registre son propre disjoncteur, sa cloison et sa politique de nouvel essai (Resilience4j). Seule une panne passagère, signalée par `ConnecteurInjoignable`, compte comme un échec : un refus du connecteur est une réponse, pas une panne.

`FileAttente` est la file des questions en attente. Elle n'a pas de stockage à elle : c'est la table des éléments de demande, lue avec `FOR UPDATE SKIP LOCKED` et un bail, ce qui permettrait à plusieurs passerelles de se partager le travail sans se marcher dessus. Elle rend les éléments réservés à la passerelle par l'interface `Reprise`.

Les réglages se trouvent sous `sira.passerelle.disjoncteur.*`, `sira.passerelle.file.*` et `sira.passerelle.collecte.*` (tentatives, attente entre tentatives, appels simultanés).

## Spécification et tests

Le module n'expose aucune route. [`openapi.yaml`](openapi.yaml) décrit la seule où son état se lit, `GET /v1/admin/etat`, servie par la passerelle et affichée par la console.

```sh
./mvnw -q -ntp verify -pl resilience -am
```

Le parcours complet, panne puis reprise, est joué par `ResilienceTest` dans `passerelle/serveur`. Sur la pile de démonstration, on provoque une panne depuis le back-office d'un registre simulé (par exemple http://casier.sira.localhost:8600), sans toucher au code.

## Avant de modifier

Faites attention à ce qui compte comme un échec : si un refus légitime ouvrait le disjoncteur, un service mal habilité suffirait à couper un registre pour tout le monde. Le comportement attendu est fixé au §6 du [contrat d'architecture](../docs/architecture/CONTRAT.md).
