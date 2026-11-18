#!/bin/bash
# Keycloak ne lit pas ses secrets dans des fichiers : ce lanceur les place dans son
# environnement, juste avant de lui céder la place.
set -euo pipefail

secrets=/run/sira/secrets

# Hors démonstration, aucun compte ni royaume fictif n'entre dans Keycloak.
if [[ "${SIRA_MODE:-socle}" != demo ]]; then
  for fichier in /opt/keycloak/royaumes/demo-*.json /opt/keycloak/royaumes/*-users-*.json; do
    if [[ -e "$fichier" ]]; then
      echo "identite: fichier de démonstration refusé hors démonstration : ${fichier##*/}" >&2
      exit 1
    fi
  done
fi

KC_DB_PASSWORD="$(<"$secrets/base.motdepasse")"
KC_BOOTSTRAP_ADMIN_PASSWORD="$(<"$secrets/amorcage.motdepasse")"
export KC_DB_PASSWORD KC_BOOTSTRAP_ADMIN_PASSWORD

# Chaque fichier de import/ devient une variable ${NOM} utilisable dans les fichiers de royaume.
for fichier in "$secrets"/import/*; do
  [[ -f "$fichier" ]] || continue
  nom="${fichier##*/}"
  [[ "$nom" =~ ^SIRA_[A-Z0-9_]+$ ]] || { echo "identite: nom de variable refusé : $nom" >&2; exit 1; }
  [[ "$nom" != SIRA_DEMO_* || "${SIRA_MODE:-socle}" == demo ]] || { echo "identite: secret de démonstration refusé hors démonstration : $nom" >&2; exit 1; }
  export "$nom=$(<"$fichier")"
done

# data/ est un volume en mémoire (racine en lecture seule) : les royaumes de l'image et
# ceux montés par la démonstration y sont déposés à chaque démarrage.
mkdir -p /opt/keycloak/data/import
cp /opt/keycloak/royaumes/*.json /opt/keycloak/data/import/

exec /opt/keycloak/bin/kc.sh "$@"
