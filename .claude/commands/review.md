---
description: "Relit les changements en cours (par rapport à main) avec le sous-agent reviewer"
argument-hint: "[consigne optionnelle]"
allowed-tools: Bash(git status:*), Bash(git diff:*)
---

## Contexte des changements

Fichiers modifiés ou nouveaux :

!`git status --short`

Résumé du diff par rapport à `main` :

!`git diff main --stat`

Diff complet :

!`git diff main`

## Tâche

Utilise le sous-agent `reviewer` pour relire les changements ci-dessus.

- Transmets-lui le diff complet et la liste des fichiers.
- Les fichiers nouveaux non suivis par git n'apparaissent pas dans le diff : demande-lui de les lire directement avec ses outils.
- Consigne supplémentaire de l'utilisateur (peut être vide) : $ARGUMENTS

Quand le sous-agent a répondu, affiche son rapport tel quel, sans le réécrire. Ne modifie aucun fichier et ne propose pas de correctif complet : c'est moi qui corrige.
