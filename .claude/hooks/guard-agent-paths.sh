#!/usr/bin/env bash
# Limite les fichiers qu'un sous-agent peut écrire. Usage : guard-agent-paths.sh tests|code|docs
mode="$1"
# Sans jq (absent de Git Bash) : extrait "file_path" et convertit les \\ Windows en /
f=$(sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)
f="${f//\\\\//}"
f="${f//\\//}"
[ -z "$f" ] && exit 0

deny() { echo "$1" >&2; exit 2; }

case "${f##*/}" in
  .env.example) ;;
  .env|.env.*) deny "Fichier .env interdit : utilise .env.example." ;;
esac

is_test=false
case "$f" in
  */src/test/*|*.spec.ts) is_test=true ;;
esac

case "$mode" in
  tests)
    $is_test || deny "test-writer : seuls les fichiers de test (src/test/, *.spec.ts) sont modifiables."
    ;;
  code)
    $is_test && deny "code-writer : les tests sont figés. Si un test te semble faux, signale-le au lieu de le modifier."
    case "$f" in
      */docs/*|*/.claude/*|*/CLAUDE.md|*/CLAUDE.local.md|*/README.md)
        deny "code-writer : documentation et configuration Claude hors périmètre (rôle du code-merger)." ;;
    esac
    ;;
  docs)
    case "$f" in
      */docs/*|*/README.md) ;;
      *) deny "code-merger : seuls docs/ et README.md sont modifiables." ;;
    esac
    ;;
  *)
    deny "guard-agent-paths : mode inconnu '$mode'."
    ;;
esac
exit 0
