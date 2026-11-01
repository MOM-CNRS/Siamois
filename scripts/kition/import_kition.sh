#!/usr/bin/env bash
# Importe Kition-Pervolia dans un serveur Siamois : crée le projet avec le gabarit « kition », y met les UE, les relations
# et les documents, puis reprend les métadonnées et les URL Nakala des documents.
#
#   export SIAMOIS_URL=https://serveur/siamois/api/v1   # se termine par /api/v1
#   export SIAMOIS_EMAIL=...        SIAMOIS_PASSWORD=...
#   ./import_kition.sh <US.xlsx> <Locus.xlsx> <organisation : nom ou id> <type de projet : libellé ou id>
set -euo pipefail
: "${SIAMOIS_URL:?définir SIAMOIS_URL}" "${SIAMOIS_EMAIL:?définir SIAMOIS_EMAIL}"
if [ -z "${SIAMOIS_PASSWORD:-}" ]; then   # sans variable, le mot de passe est demandé ici, sans écho
  [ -t 0 ] || { echo "Pas de terminal interactif : définir SIAMOIS_PASSWORD, ou lancer ce script dans un terminal." >&2; exit 2; }
  read -rs -p "Mot de passe de ${SIAMOIS_EMAIL} : " SIAMOIS_PASSWORD; echo
fi
export SIAMOIS_PASSWORD
[ $# -eq 4 ] || { echo "usage : $0 <US.xlsx> <Locus.xlsx> <organisation> <type-de-projet>   (ex. "Chypre" "Fouille programmée")" >&2; exit 2; }
# les fichiers sont donnés par rapport au dossier courant : on les rend absolus avant de changer de dossier
abs() { python3 -c 'import os,sys;print(os.path.abspath(sys.argv[1]))' "$1"; }
us=$(abs "$1"); locus=$(abs "$2")
cd "$(dirname "$0")"
log=$(mktemp)
python3 -I kition_to_siamois_api.py --us "$us" --locus "$locus" --create-project "${KITION_PROJECT_NAME:-Kition-Pervolia}" \
  --organization "$3" --project-type "$4" | tee "$log"
project=$(sed -n 's/^Projet créé : //p' "$log" | tail -1)
[ -n "$project" ] || { echo "Projet non créé : arrêt." >&2; exit 1; }
python3 -I kition_enrich_documents.py --project "$project"
echo "Terminé : projet $project (rapport : rapport_import_kition.md)"
