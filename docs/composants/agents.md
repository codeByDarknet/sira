# Utilisateurs, rôles et droits des registres et des services

Chaque registre simulé et chaque service a son propre écran d'agents. On y gère les personnes qui
travaillent dans cet espace, et ce que chacune peut faire.

## Ce que voit l'agent

- **Utilisateurs** : prénom, nom, adresse électronique, rôle, état (actif, en attente de première
  connexion, désactivé). Ajouter, modifier, désactiver, envoyer un nouveau mot de passe, retirer.
- **Rôles** : chaque rôle regroupe des droits, rangés par rubrique. Un utilisateur a un seul rôle.
  Trois rôles sont fournis et ne se suppriment pas :
  - *Administrateur* : tous les droits, y compris la gestion des utilisateurs et des rôles. Il est
    donné au premier agent de l'administration (`agent.<code>`) ;
  - *Agent* : tout le travail courant, sans gérer les utilisateurs ni les rôles ;
  - *Lecteur* : la seule consultation.
- Menus, pages et boutons n'apparaissent que si le rôle de l'agent donne le droit correspondant.

## Ajout d'un utilisateur

1. L'administrateur saisit prénom, nom, adresse électronique et rôle.
2. Le serveur crée le compte dans l'annuaire Keycloak (`demo-agents`) : identifiant = adresse
   électronique, attribut `administration` = code de l'administration, mot de passe provisoire tiré
   au hasard, actions obligatoires `UPDATE_PASSWORD` puis `CONFIGURE_TOTP`.
3. Un courriel part avec l'identifiant, le mot de passe provisoire et le lien de connexion. Le logo
   est joint au message (image « inline »), sans lien vers Internet.
4. À la première connexion, l'agent choisit son mot de passe, puis enregistre Google Authenticator
   (ou une application semblable).

## Connexion par adresse électronique

- Agents (registres, services) et administrateurs de la console se connectent avec leur adresse
  électronique et leur mot de passe, puis le code de leur application d'authentification.
- Comptes de démonstration : `instructeur@sira.example`, `autorite@sira.example`,
  `auditeur@sira.example`, `agent.<code>@sira.example` (par exemple
  `agent.etat-civil@sira.example`). Ces adresses sont fictives (domaine réservé `.example`) : tout
  le monde, jury compris, s'y connecte avec le mot de passe commun (`scripts/sira comptes`). Elles
  ne reçoivent aucun courriel ; seuls les comptes créés depuis l'écran Utilisateurs, avec une vraie
  adresse, reçoivent leurs identifiants et le lien « Afficher un nouveau QR code ».

## Mots de passe et téléphone perdu

- Règles de l'annuaire (royaumes des agents et de la console) : 12 caractères au moins, une
  majuscule, une minuscule, un chiffre, un signe, ni l'identifiant ni l'adresse, pas un des trois
  derniers mots de passe. Le mot de passe provisoire tiré par Sira les respecte.
- Téléphone perdu : sur la page du code, « Afficher un nouveau QR code » envoie un lien à
  l'adresse de l'agent ; en l'ouvrant, il choisit un nouveau mot de passe puis scanne un nouveau
  QR code (flux « reset credentials » de Keycloak, avec « Reset OTP »). Depuis « Mon profil »,
  un agent connecté peut aussi afficher un nouveau QR code.

## Documents d'une personne (registres)

- Une pièce se dépose avec son fichier (PDF, PNG ou JPEG, 5 Mo au plus, type vérifié sur les
  premiers octets), son type (un document du registre ou un autre), son nom et les informations
  qu'elle contient. Droit `DOCUMENTS_DEPOSER`.
- Une pièce PDF d'un type que délivre le registre remplace le document produit, y compris pour
  Sira (API interne).
- L'aperçu dessine le PDF avec pdf.js dans une fenêtre (les réponses de l'API ne peuvent pas être
  affichées dans un cadre).
- Les adresses du back-office désignent une personne et un rôle par un UUID ; l'identifiant unique
  (douze chiffres) reste la donnée métier de l'API interne.

## Sécurité

- **Chaque route est protégée côté serveur** par un droit (`AccesAgents.exige(...)`), en plus de
  l'appartenance à l'administration. Une route non déclarée est refusée (`denyAll`). Les droits sont
  relus à chaque requête : un rôle retiré prend effet tout de suite.
- L'écran ne fait que montrer ou cacher : le serveur reste seul juge.
- Le serveur parle à Keycloak avec son certificat (mTLS, `tls_client_auth`) et le compte de service
  de son client (`manage-users`, `view-users`, `query-users`). Il ne touche qu'aux comptes dont
  l'attribut `administration` est le sien. Limite de la démonstration : ce compte de service a les
  droits sur tout le royaume des agents ; en production, chaque administration aurait son royaume
  ou des permissions fines.
- Un agent ne peut ni supprimer son propre compte, ni changer son propre rôle, ni se désactiver.
- Le mot de passe SMTP vit dans `deploiement/.env` (hors dépôt). Les serveurs des registres et des
  services sont les seuls reliés au réseau de sortie `courriel`.

## Où est le code

| Partie | Emplacement |
|---|---|
| Droits, rôles, annuaire, courriel | `commun/src/main/java/bf/sira/commun/agents/` |
| Tables | `V2__agents.sql` (registres), `V3__agents.sql` (services) |
| Droits du registre et règles des routes | `registres/serveur/.../configuration/Securite.java` |
| Droits du service et règles des routes | `guichet-agent/serveur/.../configuration/Securite.java` |
| Pages Utilisateurs et Rôles | `interfaces/demo/commun/agents/` |
| Réglages SMTP | `deploiement/.env` (`SIRA_COURRIEL_*`), modèle dans `.env.example` |
