# Commun

Les briques Java que partagent tous les composants de Sira, de la passerelle aux registres simulés. Elles existent pour qu'une règle de sécurité ne soit écrite qu'une fois : vérifier un certificat client, signer une requête, chiffrer une attestation pour un seul destinataire ou calculer un pseudonyme se fait toujours de la même manière, quel que soit le module.

Le module `sira-commun` se configure de lui-même (`SiraAutoConfiguration`) dès qu'une application Spring Boot l'ajoute à ses dépendances. Ses paquets se lisent presque comme une table des matières :

- `identite` et `mtls` : le TLS mutuel entre machines, où l'identité d'un membre est l'URI inscrite dans son certificat (`SecuriteMembres`, `ClientsMtls`) ;
- `pki` : la lecture des clés et certificats montés dans le conteneur ;
- `jose` : les requêtes signées (`Signataire`, `VerificateurJws`, protection contre le rejeu) et les attestations signées puis chiffrées (`ScelleurAttestation`, `LecteurAttestation`) ;
- `crypto` : les pseudonymes propres à chaque service (`Pseudonymes`) et les comparaisons en temps constant ;
- `oidc` et `jetons` : la connexion des utilisateurs par Keycloak, côté serveur, et les jetons liés au certificat ;
- `erreurs` et `donnees` : les réponses d'erreur au format RFC 9457, la limite de taille des corps, la pagination.

Il n'a pas de route HTTP propre, donc pas de spécification OpenAPI. Ses tests se lancent ainsi :

```sh
./mvnw -q -ntp verify -pl commun
```

Avant de modifier quoi que ce soit ici, gardez en tête que tout le socle en dépend : un changement dans `jose` ou `mtls` touche chaque échange. N'y ajoutez pas d'algorithme maison ; les briques s'appuient sur des bibliothèques éprouvées et vérifient strictement ce qu'elles reçoivent. Le détail des briques et la façon de s'en servir sont dans [docs/developpement/backend.md](../docs/developpement/backend.md), et les règles qu'elles appliquent au §5 du [contrat d'architecture](../docs/architecture/CONTRAT.md).
