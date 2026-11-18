#!/bin/sh
# Autorité de certification interne de Sira et générateur de secrets.
#
#   pki initialiser                        émet tout ce que déclarent les fichiers de configuration
#   pki emettre <code> <uri> <dns> [uid]   émet les trois paires d'un membre non déclaré
#   pki lister                             affiche les certificats émis et leur échéance
#   pki comptes                            affiche les secrets de démonstration (préfixe « demo- »)
#
# SIRA_MODE (socle ou demo) : mode de ce démarrage. Les volumes gardent celui qui les
# a créés ; un autre est refusé, pour qu'aucun compte fictif ne se retrouve dans un socle seul, et
# que le transport des registres, inscrit en base au premier démarrage, reste celui du mode.
#
# Idempotent : une clé ou un secret existant n'est jamais régénéré ; un certificat n'est réémis
# (avec une nouvelle clé) que s'il expire bientôt.
#
# Volumes :
#   /autorite  clé de l'autorité et exemplaire maître des secrets — monté par ce conteneur seul
#   /pki       membres/<code>/{ca.crt,tls.*,cachet.*,chiffrement.*} — chaque membre monte SON dossier
#   /secrets   <composant>/<fichier> — chaque composant monte SON dossier
set -eu
umask 077

CONF="${SIRA_CONF:-/etc/sira}"
AUTORITE=/autorite
PKI=/pki
SECRETS=/secrets
UID_DEFAUT="${SIRA_UID_APPLICATION:-10001}"
JOURS_AUTORITE="${SIRA_PKI_JOURS_AUTORITE:-1825}"
JOURS_CERTIFICAT="${SIRA_PKI_JOURS_CERTIFICAT:-90}"
# Un certificat qui expire dans moins de 30 jours est réémis au démarrage suivant.
SEUIL_RENOUVELLEMENT=2592000
SUJET_AUTORITE="/O=Sira/CN=AC Sira"

dire() { printf 'pki: %s\n' "$*"; }
echouer() { printf 'pki: erreur: %s\n' "$*" >&2; exit 1; }

# Lit les lignes utiles (hors commentaires et lignes vides) des fichiers <préfixe>*.conf.
lignes() {
  for fichier in "$CONF"/"$1"*.conf; do
    [ -f "$fichier" ] && sed -e 's/#.*//' -e '/^[[:space:]]*$/d' "$fichier"
  done
  return 0
}

cle_p256() { openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$1"; }

# Place un fichier chez son lecteur : droits d'abord, propriétaire ensuite (seule CAP_CHOWN est requise).
remettre() { # <fichier> <mode> <uid>
  chmod "$2" "$1"
  chown "$3:$3" "$1"
}

creer_autorite() {
  mkdir -p "$AUTORITE/secrets" "$PKI/membres"
  chmod 755 "$PKI" "$PKI/membres" "$SECRETS"
  if [ ! -s "$AUTORITE/ca.key" ]; then
    cle_p256 "$AUTORITE/ca.key"
    openssl req -x509 -new -sha256 -key "$AUTORITE/ca.key" -days "$JOURS_AUTORITE" \
      -subj "$SUJET_AUTORITE" \
      -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
      -addext "keyUsage=critical,keyCertSign,cRLSign" \
      -out "$AUTORITE/ca.crt"
    dire "autorité créée ($(openssl x509 -in "$AUTORITE/ca.crt" -noout -fingerprint -sha256))"
  fi
}

# Extensions X.509 d'une paire selon son usage. Jamais la même clé pour deux usages.
extensions() { # <usage> <uri> <dns séparés par des virgules>
  printf '%s\n' "basicConstraints=critical,CA:FALSE" "subjectKeyIdentifier=hash" "authorityKeyIdentifier=keyid"
  case "$1" in
    tls)
      san="URI:$2"
      for nom in $(printf '%s' "$3" | tr ',' ' '); do san="$san,DNS:$nom"; done
      printf '%s\n' "keyUsage=critical,digitalSignature" "extendedKeyUsage=serverAuth,clientAuth" "subjectAltName=$san" ;;
    cachet)
      printf '%s\n' "keyUsage=critical,digitalSignature" "subjectAltName=URI:$2" ;;
    chiffrement)
      printf '%s\n' "keyUsage=critical,keyAgreement" "subjectAltName=URI:$2" ;;
    *) echouer "usage inconnu : $1" ;;
  esac
}

emettre_paire() { # <code> <uri> <dns> <uid> <usage>
  dossier="$PKI/membres/$1"
  certificat="$dossier/$5.crt"
  if [ -s "$certificat" ] && [ -e "$dossier/$5.key" ] \
    && openssl x509 -in "$certificat" -noout -checkend "$SEUIL_RENOUVELLEMENT" >/dev/null; then
    return 0
  fi
  travail="$(mktemp -d)"
  cle_p256 "$travail/cle"
  # L'usage figure dans le sujet : le certificat de cachet d'un membre ne peut pas
  # passer pour son certificat de transport auprès de Keycloak (qui compare le sujet).
  openssl req -new -sha256 -key "$travail/cle" -subj "/O=Sira/OU=$5/CN=$2" -out "$travail/csr"
  extensions "$5" "$2" "$3" > "$travail/ext"
  openssl x509 -req -sha256 -in "$travail/csr" -CA "$AUTORITE/ca.crt" -CAkey "$AUTORITE/ca.key" \
    -CAserial "$AUTORITE/ca.srl" -CAcreateserial -days "$JOURS_CERTIFICAT" \
    -extfile "$travail/ext" -out "$travail/crt" 2>/dev/null
  rm -f "$certificat" "$dossier/$5.key"
  cp "$travail/crt" "$certificat"; chmod 644 "$certificat"
  cp "$travail/cle" "$dossier/$5.key"; remettre "$dossier/$5.key" 400 "$4"
  rm -rf "$travail"
  dire "certificat émis : $1/$5 ($2)"
}

emettre_membre() { # <code> <uri> <dns> [uid] [paires]
  code="$1"; uri="$2"; dns="$3"; uid="${4:-$UID_DEFAUT}"; paires="${5:-tls,cachet,chiffrement}"
  printf '%s' "$code" | grep -Eq '^[a-z0-9][a-z0-9-]*$' || echouer "code de membre invalide : $code"
  printf '%s' "$uri" | grep -Eq '^urn:sira:[a-z0-9-]+:[a-z0-9-]+$' || echouer "URI invalide : $uri"
  printf '%s' "$dns" | grep -Eq '^[a-z0-9.,-]+$' || echouer "noms DNS invalides : $dns"
  printf '%s' "$uid" | grep -Eq '^[0-9]+$' || echouer "uid invalide : $uid"
  mkdir -p "$PKI/membres/$code"
  chmod 755 "$PKI/membres/$code"
  cp "$AUTORITE/ca.crt" "$PKI/membres/$code/ca.crt"
  chmod 644 "$PKI/membres/$code/ca.crt"
  for usage in $(printf '%s' "$paires" | tr ',' ' '); do
    emettre_paire "$code" "$uri" "$dns" "$uid" "$usage"
  done
}

generer() { # <genre>
  case "$1" in
    hex) openssl rand -hex 24 ;;
    cle) openssl rand -base64 32 ;;
    # Court, destiné à être tapé. Le préfixe et le chiffre exigé le rendent conforme aux règles de
    # mot de passe des royaumes (majuscule, minuscule, chiffre, signe) : sans cela, Keycloak refuse
    # l'import des comptes de démonstration et ne démarre pas. L'aléa reste celui d'openssl (80 bits).
    court)
      while :; do
        alea="$(openssl rand -hex 10)"
        case "$alea" in *[0-9]*) break ;; esac
      done
      printf 'Sira-%s\n' "$alea" ;;
    *) echouer "genre de secret inconnu : $1" ;;
  esac
}

# Un secret, un exemplaire maître, autant de copies que de lecteurs.
distribuer_secret() { # <nom> <genre> <composant/fichier:uid>...
  nom="$1"; genre="$2"; shift 2
  maitre="$AUTORITE/secrets/$nom"
  if [ ! -s "$maitre" ]; then
    generer "$genre" > "$maitre"
    dire "secret généré : $nom"
  fi
  for destination in "$@"; do
    chemin="$SECRETS/${destination%%:*}"; uid="${destination##*:}"
    # Chaque dossier traversé doit rester lisible par le composant qui le monte.
    dossier="$(dirname "$chemin")"
    mkdir -p "$dossier"
    while [ "$dossier" != "$SECRETS" ]; do chmod 755 "$dossier"; dossier="$(dirname "$dossier")"; done
    rm -f "$chemin"
    cp "$maitre" "$chemin"
    remettre "$chemin" 400 "$uid"
  done
}

# Le mode est inscrit dans /autorite/mode. Des volumes plus anciens, sans cette marque, sont
# reconnus à leurs secrets de démonstration.
verifier_mode() {
  attendu="${SIRA_MODE:-socle}"
  case "$attendu" in socle | demo) ;; *) echouer "SIRA_MODE inconnu : $attendu (socle ou demo)" ;; esac
  if [ -s "$AUTORITE/mode" ]; then
    actuel="$(cat "$AUTORITE/mode")"
  elif ls "$AUTORITE"/secrets/demo-* > /dev/null 2>&1; then
    actuel=demo
  elif [ -s "$AUTORITE/ca.key" ]; then
    actuel=socle
  else
    actuel="$attendu"
  fi
  if [ "$actuel" != "$attendu" ]; then
    case "$actuel" in
      demo) commande="scripts/sira demo" ;;
      *) commande="scripts/sira demarrer" ;;
    esac
    echouer "ces volumes ont été créés en mode « $actuel », ce démarrage est en mode « $attendu ».
  Relancer dans le même mode : $commande
  Ou tout effacer (données, certificats, secrets) : scripts/sira reinitialiser"
  fi
  printf '%s\n' "$attendu" > "$AUTORITE/mode"
}

initialiser() {
  verifier_mode
  creer_autorite
  lignes membres | while read -r code uri dns uid paires; do
    emettre_membre "$code" "$uri" "$dns" "$uid" "$paires"
  done
  lignes secrets | while read -r nom genre destinations; do
    # shellcheck disable=SC2086  # découpage voulu : une destination par mot
    distribuer_secret "$nom" "$genre" $destinations
  done
  dire "prêt"
}

lister() {
  for certificat in "$PKI"/membres/*/*.crt; do
    [ "${certificat##*/}" = ca.crt ] && continue
    printf '%-44s %s\n' "${certificat#"$PKI"/membres/}" "$(openssl x509 -in "$certificat" -noout -enddate | cut -d= -f2)"
  done
}

comptes() {
  for maitre in "$AUTORITE"/secrets/demo-*; do
    [ -f "$maitre" ] || { dire "aucun secret de démonstration"; return 0; }
    nom="${maitre##*/}"
    case "$nom" in
      # Graines TOTP des versions précédentes : plus utilisées, chaque compte enrôle son application.
      demo-totp-*) ;;
      *) printf '%-28s %s\n' "$nom" "$(cat "$maitre")" ;;
    esac
  done
}

case "${1:-initialiser}" in
  initialiser) initialiser ;;
  emettre)
    [ $# -ge 4 ] || echouer "usage : pki emettre <code> <uri> <dns> [uid]"
    creer_autorite
    emettre_membre "$2" "$3" "$4" "${5:-}"
    ;;
  lister) lister ;;
  comptes) comptes ;;
  mode) verifier_mode ;;
  *) echouer "commande inconnue : $1" ;;
esac
