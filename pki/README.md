# PKI

Dans Sira, une machine prouve qui elle est par son certificat : la passerelle n'accepte une demande que d'un service dont le certificat porte l'identité attendue (par exemple `urn:sira:service:concours`), et un connecteur ne répond qu'à la passerelle. Il faut donc une autorité qui émette ces certificats. Ce dossier la fournit, avec le générateur des secrets du déploiement (mots de passe des bases, clé de pseudonymisation, etc.), pour que rien de tout cela n'ait jamais à être versionné.

Le tout tient dans un script shell, [`pki.sh`](pki.sh), qui ne s'appuie que sur OpenSSL et tourne dans un petit conteneur Alpine. Au démarrage de la pile, le conteneur `pki` lit les fichiers `membres*.conf` et `secrets*.conf` de [`deploiement/`](../deploiement/README.md), crée l'autorité si elle n'existe pas, puis émet ce qui manque. Un membre reçoit par défaut trois paires de clés ECDSA P-256 distinctes, une par usage : `tls` pour le transport, `cachet` pour signer, `chiffrement` pour recevoir des attestations chiffrées. Chaque conteneur ne monte que son propre dossier de clés et ses propres secrets.

Le script est idempotent : une clé ou un secret existant n'est jamais régénéré, et un certificat n'est réémis (avec une nouvelle clé) que s'il lui reste moins de trente jours sur ses quatre-vingt-dix. On s'en sert à travers `scripts/sira` :

```sh
scripts/sira pki lister                         # certificats émis et leur échéance
scripts/sira pki emettre <code>                 # un membre déclaré dans deploiement/membres*.conf
scripts/sira pki emettre <code> <uri> <dns> [uid]   # un membre non déclaré, pour un essai
scripts/sira comptes                            # mot de passe commun de la démonstration
```

Les volumes retiennent le mode qui les a créés (socle seul ou démonstration), et le script refuse un démarrage dans un autre mode. C'est ce qui empêche un compte fictif de se retrouver dans un socle de production ; pour changer de mode, il faut d'abord `scripts/sira reinitialiser`.

Il n'y a ni module Maven, ni spécification OpenAPI, ni tests propres à ce dossier : la lecture des certificats est éprouvée dans [`commun/`](../commun/README.md) et le déploiement par `scripts/sira verifier`. Avant de modifier le script, rappelez-vous qu'il tourne à chaque démarrage sur des volumes existants : il doit continuer à accepter ce qu'il a lui-même produit auparavant. Pour un vrai déploiement, la clé de l'autorité devrait vivre hors ligne ou dans un HSM, et la révocation n'est pas encore prise en charge ; voir [docs/securite.md](../docs/securite.md).
