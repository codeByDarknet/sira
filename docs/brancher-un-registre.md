# Brancher un registre

Une administration tient déjà son registre, avec sa propre API, ses noms de champs et sa manière
de s'authentifier. Elle ne devrait pas avoir à le réécrire pour rejoindre Sira. On pose donc devant
lui un connecteur, décrit par deux fichiers : **comment lire** le registre (le raccordement) et
**ce qui peut en sortir** (la politique locale). La passerelle ne parle qu'au connecteur, en mTLS,
par requête signée ; le connecteur répond par une attestation signée puis chiffrée pour le seul
service demandeur. Le registre ne voit jamais ni la passerelle ni le service.

Le dépôt contient un exemple complet, que l'on peut rejouer : un registre « étranger », écrit
autrement que ceux de Sira, branché par `scripts/sira etranger` (voir plus bas).

## Ce qu'il faut demander à l'administration qui tient le registre

| Question | Exemple (registre de l'exemple) |
|---|---|
| Adresse de base | `http://registre-etranger:8080` |
| Comment lire une personne | `GET /registry/v2/citizens/lookup?nin=<identifiant>` |
| Où est la personne dans la réponse | `{"data": {"citizen": {...}}}` |
| Comment s'authentifier | en-tête `X-Api-Key` |
| Le nom des champs utiles | `birth.date`, `name.family`, `name.given` (liste) |
| Ce que répond un inconnu | `404` |

L'identifiant est l'identifiant unique à 12 chiffres. Un registre qui répond `200` avec un corps
vide pour un inconnu est traité comme « personne inconnue » si la `racine` est absente ou `null`.

## Les étapes

**1. Le fichier de raccordement.** Modèle : `registres/etranger/raccordement.yaml`.

```yaml
adresse: http://registre-etranger:8080            # schéma, hôte, port ; HTTPS hors démonstration
fiche:
  chemin: /registry/v2/citizens/lookup?nin={identifiant}
  racine: /data/citizen
authentification:
  type: CLE_API                                   # AUCUNE, JETON, CLE_API, MTLS
  en_tete: X-Api-Key
  fichier: /run/sira/secrets/cle-api              # monté, jamais dans le dépôt
correspondance:                                   # nom employé par les questions -> réponse
  date_naissance: /birth/date
  nom: /name/family
  prenoms: name.given[*]
```

- `methode: POST` et `corps: '{"nin": "{identifiant}"}'` si le registre se lit par POST.
- `JETON` envoie `Authorization: Bearer <contenu du fichier>` ; `MTLS` prend `certificat`, `cle`
  et `autorite` (fichiers PEM).
- Emplacements : pointeur JSON (`/a/b`, `/liste/*/champ`) ou chemin simple (`a.b`, `liste[*].champ`).
  Rien d'autre n'est lu ; rien de la réponse brute ne sort du connecteur.
- Erreurs : `404` = personne inconnue ; tout le reste (délai, `5xx`, autre type, trop gros,
  racine qui n'est pas un objet) = « registre indisponible ».

**2. La politique locale.** Modèle : `registres/etranger/politique.yaml`. Uniquement ce
que l'administration accepte d'exposer, avec les noms de la correspondance :

```yaml
validite: { par_defaut: P90D, maximale: P180D }
champs:
  - { chemin: date_naissance, niveau: STATUT, operateurs: [AGE_AU_MOINS], valeurs: [18] }
documents: []
```

**3. Le membre et ses secrets.** Une ligne dans `deploiement/membres.demo.conf` :

```
registre-etranger  urn:sira:registre:etranger  connecteur-etranger  10001
```

et, dans `deploiement/secrets.demo.conf`, la base du connecteur (et, pour l'exemple, la clé
d'API, générée et remise aux deux côtés) :

```
base-connecteur-etranger   hex  socle-db/roles/connecteur_etranger:70  registre-etranger/base.motdepasse:10001
cle-api-registre-etranger  hex  etranger/cle-api:1000                  registre-etranger/cle-api:10001
```

Si la clé est fournie par l'administration, ne l'écrivez dans aucun fichier du dépôt : déposez-la dans un
fichier du poste hors dépôt (lisible par l'uid 10001, mode 0400), montez-le en lecture seule dans
le connecteur et faites-y pointer `fichier`. Puis :

```sh
scripts/sira pki emettre registre-etranger   # certificats et secrets du membre
scripts/sira bases                            # base du journal du connecteur
```

**4. Le service compose.** Modèle : `connecteur-etranger` dans
`deploiement/compose.demo.yaml`. Une instance de l'image `sira/connecteur:local` avec :

```yaml
SIRA_CONNECTEUR_REGISTRE_CODE: etranger
SIRA_CONNECTEUR_REGISTRE_RACCORDEMENT: /etc/sira/raccordement.yaml
SIRA_CONNECTEUR_REGISTRE_HOTESPERMIS: registre-etranger   # seul hôte interne admis
SIRA_CONNECTEUR_REGISTRE_HTTPADMIS: "true"                 # seulement si le registre n'a pas HTTPS
```

les deux fichiers montés en `/etc/sira/`, les volumes `pki` et `secrets` du membre, et les réseaux
`sira`, `donnees` et celui où le registre est joignable. Un registre hors de la machine se joint
par son nom public : sans `HOTESPERMIS`, son adresse doit être publique (les adresses internes
sont refusées), et HTTPS est exigé. Le connecteur refuse de démarrer si un fichier est invalide.

**5. La console**, avec le compte `instructeur@sira.example`, puis `autorite@sira.example` :

1. `/membres` : déclarer le membre `etranger`, rôle registre, identité `urn:sira:registre:etranger` ;
2. `/registres/nouveau` : code `etranger`, adresse `https://connecteur-etranger:8443`, transport direct ;
3. `/questions/nouvelle` : registre `etranger`, oui ou non, `date_naissance`, âge au moins 18 ;
   l'essai sur `990048217350` répond « oui » ; publier ;
4. habilitation : la démarche et la question, soumise par l'instructeur, approuvée par l'autorité.

Le citoyen voit la question sur l'écran d'accord ; le service reçoit « oui » ; `/journal` montre
la consultation du registre `etranger`.

## L'exemple rejouable

```sh
scripts/sira demo        # si la démonstration ne tourne pas
scripts/sira etranger    # registre étranger, son connecteur (profil compose « etranger ») et le scénario
scripts/sira etranger arreter
```

Le registre de l'exemple (`registres/etranger/`) est volontairement différent des registres de
Sira : Node sans dépendance au lieu de Java, champs anglais imbriqués, identifiant en paramètre
d'URL, réponse enveloppée, HTTP simple avec clé d'API. Le scénario
`tests/e2e/scenarios/etranger/registre-etranger.spec.ts` déclare le membre et le registre, essaie
puis publie la question, fait approuver une habilitation par un second compte, inscrit
Hamidou DICKO au concours et attend « majeur : oui », vérifie la consultation au journal et son
intégrité. Le concours ne connaît qu'une démarche : son habilitation est suspendue le temps de
l'exemple, puis rétablie.

## Si l'administration expose sa propre passerelle

Alors ce n'est pas un registre brut mais un autre système d'échange, avec ses propres accords.
Deux cas :

- **Elle offre une lecture simple par personne** (un point d'accès de sa passerelle qui rend une
  fiche JSON, même enveloppée) : on la traite comme un registre. Le connecteur s'authentifie
  auprès d'elle comme elle l'exige (`JETON`, `CLE_API` ou `MTLS` avec le certificat qu'elle
  délivre). Ce qu'elle a déjà filtré reste filtré : la politique locale ne peut que restreindre.
- **Elle n'offre que son propre protocole** (requêtes signées, accord du citoyen chez elle,
  réponses chiffrées) : un fichier de raccordement ne suffit pas. Il faut un adaptateur qui parle
  son protocole et rend une fiche au connecteur, ou un accord de fédération entre passerelles
  (comme X-Road). La signature et le
  chiffrement de ses réponses ne sont alors pas vérifiés par Sira : c'est une limite à annoncer.

Dans les deux cas, l'autre passerelle voit l'identifiant unique de la personne demandée : c'est
le prix de la lecture par personne, et il faut le dire à ceux qui décident du raccordement.

## Limites

- La garde contre les adresses internes résout le nom au démarrage du connecteur : un nom qui
  change d'adresse ensuite (rebinding DNS) n'est pas couvert. Hors démonstration, préférez une
  adresse publique stable et un pare-feu de sortie.
- Les secrets (clé, jeton) sont lus au démarrage : une rotation demande un redémarrage du connecteur.
- Un registre qui n'identifie pas les personnes par l'identifiant unique à 12 chiffres demande une
  table de correspondance chez lui : le connecteur ne traduit pas les identifiants.
