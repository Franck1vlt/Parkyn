#!/usr/bin/env bash
# Sans jq (absent de Git Bash) : extrait "file_path" et convertit les \\ Windows en /
f=$(sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)
f="${f//\\\\//}"
f="${f//\\//}"
case "$f" in
  */db/migration/*.sql)
    if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
      echo "Migration Flyway déjà commitée : crée une nouvelle migration V<n+1>." >&2
      exit 2
    fi
    ;;
esac
exit 0