#!/usr/bin/env bash
f=$(jq -r '.tool_input.file_path // empty')
case "$f" in
  */db/migration/*.sql)
    if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
      echo "Migration Flyway déjà commitée : crée une nouvelle migration V<n+1>." >&2
      exit 2
    fi
    ;;
esac
exit 0