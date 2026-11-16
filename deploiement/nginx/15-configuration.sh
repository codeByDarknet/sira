#!/bin/sh
# Prépare /tmp/sira avant que l'image ne produise la configuration nginx, et écrit
# /config.json : chaque variable CONFIG_X devient la clé « x » (CONFIG_NOM → "nom").
# Le même conteneur sert ainsi plusieurs plateformes, sans reconstruction.
set -eu

dossier="${NGINX_ENVSUBST_OUTPUT_DIR:-/tmp/sira}"
mkdir -p "$dossier"

awk 'BEGIN {
  printf "{"
  for (nom in ENVIRON) {
    if (nom !~ /^CONFIG_[A-Z0-9_]+$/) continue
    printf "%s\"%s\":\"", (n++ ? "," : ""), tolower(substr(nom, 8))
    valeur = ENVIRON[nom]
    # Caractère par caractère : le remplacement de « \ » par gsub varie d un awk à l autre.
    for (i = 1; i <= length(valeur); i++) {
      c = substr(valeur, i, 1)
      if (c == "\\" || c == "\"") printf "\\%s", c
      else if (c < " ") printf " "
      else printf "%s", c
    }
    printf "\""
  }
  print "}"
}' > "$dossier/config.json"
