#!/bin/sh
# Crée, dans cette instance PostgreSQL, un rôle et une base par fichier de /run/sira/secrets/roles/
# (nom du fichier = nom du rôle, contenu = mot de passe). Idempotent : exécuté à l'initialisation
# de l'instance, puis à la demande (« scripts/sira bases ») quand un composant est ajouté.
set -eu

sql() { psql --username postgres --dbname postgres --no-psqlrc --quiet --set ON_ERROR_STOP=1 "$@"; }

# Par défaut, tout rôle peut se connecter à la base « postgres » : on le retire.
sql --command 'REVOKE CONNECT ON DATABASE postgres FROM PUBLIC'

for fichier in /run/sira/secrets/roles/*; do
  [ -f "$fichier" ] || continue
  # Le mot de passe passe par l'entrée de psql, jamais par sa ligne de commande (visible dans ps).
  sql --set role="${fichier##*/}" --set fichier="$fichier" <<'SQL'
\set mot_de_passe `cat :'fichier'`
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT', :'role')
 WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'role') \gexec
ALTER ROLE :"role" PASSWORD :'mot_de_passe';
SELECT format('CREATE DATABASE %I OWNER %I', :'role', :'role')
 WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'role') \gexec
-- Chaque composant n'accède qu'à sa base.
REVOKE ALL ON DATABASE :"role" FROM PUBLIC;
SQL
done
