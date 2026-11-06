---
name: multi-agent-tdd-pipeline
description: "Workflow TDD complet d'une tâche Parkyn : test-planner → test-writer → vérification du rouge → code-writer → code-reviewer → code-merger, avec validation de Franck à chaque étape clé."
argument-hint: "<tâche ou référence dans la checklist du lot>"
disable-model-invocation: true
---

# Workflow TDD

Tâche demandée : $ARGUMENTS

Tu orchestres la chaîne. Chaque sous-agent démarre sans contexte : transmets-lui à chaque appel tout ce dont il a besoin (tâche, plan validé, sorties des étapes précédentes utiles). Les sous-agents ne peuvent pas s'appeler entre eux : c'est toi qui enchaînes.

Points d'arrêt ⏸ : tu t'arrêtes et tu attends la réponse de Franck. Tu n'avances jamais sans son accord à ces endroits.

## Étape 1 : plan de tests (`test-planner`)

- Transmets la tâche, et l'extrait de `docs/lots/` qui la décrit si elle y figure.
- Affiche le plan tel quel, avec les questions ouvertes.
- ⏸ **Franck valide le plan**, le corrige ou répond aux questions. Boucle avec `test-planner` jusqu'à validation.

## Étape 2 : écriture des tests (`test-writer`)

- Transmets la tâche et le plan **validé** (avec les corrections de Franck).
- Si `test-writer` signale des écarts au plan, montre-les à Franck avant de continuer.

## Étape 3 : vérification du rouge (toi)

1. Crée les signatures minimales listées (corps vide ou `throw new UnsupportedOperationException()`), sans aucune logique, pour que les tests compilent.
2. Lance chaque test annoncé :
   - backend : `cd parkyn-api && ./mvnw test -Dtest=...` (Docker doit tourner) ;
   - frontend : `cd parkyn-web && export PATH="/c/Users/valmo/.nvm/versions/node/v24.15.0/bin:$PATH" && npm test -- --watch=false`.
3. Compare test par test le rouge obtenu au rouge annoncé, dans un tableau `Test | Rouge annoncé | Rouge obtenu | Conforme`.
   - Un test déjà vert, une erreur de compilation ou un échec pour une autre raison (`400` au lieu de `401`, etc.) n'est pas conforme.
   - Non conforme → retour à `test-writer` avec l'écart. Si c'est le plan qui est faux, retour à `test-planner`, puis ⏸ nouvelle validation de Franck.
   - Boucle jusqu'à ce que tous les rouges soient conformes.
4. ⏸ **Franck valide le rouge.**
5. Fige les tests : `git add` de chaque fichier de test créé ou modifié (nommés un par un). C'est la référence que `code-reviewer` comparera.

## Étape 4 : code (`code-writer`)

- Transmets la tâche, le plan validé, la liste des fichiers de test et la sortie rouge.
- Si `code-writer` fait un signalement (test faux, invariant, dépendance), ⏸ montre-le à Franck avant de continuer.

## Étape 5 : relecture (`code-reviewer`)

- Transmets la tâche, le plan validé, la liste des fichiers de test figés et le résumé de `code-writer`.
- Affiche son rapport tel quel.
- `À reprendre` → renvoie les constats à `code-writer`, puis repasse par `code-reviewer`. Boucle jusqu'à `Validé`. Si le même constat revient deux fois, préviens Franck.
- `Bloqué` → ⏸ Franck tranche.
- `Validé` → ⏸ **Franck valide les verts.**

## Étape 6 : documentation et commit (`code-merger`)

1. Appelle `code-merger` en **mode rapport** avec la tâche, le plan, le rapport de `code-reviewer` et la liste des fichiers.
2. Affiche le rapport de tâche tel quel.
3. ⏸ **Franck valide le rapport et le message de commit** (ou les corrige).
4. Appelle `code-merger` en **mode commit** avec la liste des fichiers et le message validés.
5. Fin : rappelle que le push sur `dev` et la PR sont faits par Franck.
