# Journal

Quand Aïcha ouvre son espace, elle voit que le service des concours a demandé à l'état civil si elle était majeure, quand, avec quel accord, et que la réponse a bien été transmise. Ce dossier est ce qui rend cette page digne de confiance.

Chaque consultation, chaque accord et chaque retrait laisse une ligne dans le journal. Une ligne porte le hachage SHA-256 de la précédente, si bien qu'on ne peut en retirer ou en modifier une sans casser tout ce qui suit. À intervalle régulier, la passerelle signe l'extrémité de la chaîne avec sa clé de cachet : ces points de contrôle empêchent de réécrire le journal en entier sans que cela se voie. La base, enfin, refuse toute modification ou suppression dans les tables du journal. Si une ligne lui paraît anormale, le citoyen peut la signaler, et le signalement arrive dans la console.

## Comment il s'articule

Le module `sira-journal` est une bibliothèque embarquée dans la [passerelle](../passerelle/README.md) : la ligne s'écrit dans la même transaction que l'accord ou l'échange qu'elle trace, donc l'un n'existe jamais sans l'autre. On y écrit par `Journal.ecrire(Trace)`, on y lit par `LectureJournal`, et `VerificationJournal` recalcule la chaîne et contrôle les signatures, ce que la console propose depuis son écran du journal. Le module n'attend qu'une interface, `CitoyensJournal`, fournie par le [consentement](../consentement-minimisation/README.md). Les tables, elles, sont créées par la migration de la passerelle.

## Spécification et tests

[`openapi.yaml`](openapi.yaml) décrit les routes du citoyen (`/v1/citoyen/journal`, et le signalement d'une ligne) et celles de la console (`/v1/admin/journal`, sa vérification, les signalements et leur traitement). Toutes sont servies par la passerelle.

```sh
./mvnw -q -ntp verify -pl journal -am
```

Ces tests vérifient le chaînage et comparent les routes à la spécification. Le comportement avec une vraie base (refus de modification, points de contrôle, vérification) est éprouvé par `JournalTest`, dans `passerelle/serveur`.

## Avant de modifier

Le format d'une ligne et la façon dont son hachage est calculé font partie du contrat : un changement rendrait illisibles, ou faussement corrompus, tous les journaux déjà écrits. Ajouter un type de ligne est possible, mais il doit aussi apparaître dans `openapi.yaml`, que `ContratOpenApiTest` confronte à `TypeLigne`. Le contrat du journal est décrit au §10 du [contrat d'architecture](../docs/architecture/CONTRAT.md).
