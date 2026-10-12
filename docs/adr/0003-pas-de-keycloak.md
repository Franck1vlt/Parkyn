# ADR-0003 : Pas de Keycloak

- Statut : Accepté
- Date : 2026-10-05

## Contexte

L'application nécessite un système d'authentification permettant de gérer les utilisateurs, leurs sessions et leurs droits d'accès.

Keycloak est une solution mature d'Identity and Access Management (IAM), mais son adoption représente également un coût technique et opérationnel important.

Le projet ne nécessite actuellement ni SSO d'entreprise, ni fédération avec plusieurs fournisseurs d'identité, ni MFA complexe. L'authentification peut donc être gérée directement par l'application via une API backend et une gestion des tokens.

### Contraintes

- Le projet doit rester simple à déployer et à maintenir.
- L'infrastructure doit être limitée au nécessaire.
- L'authentification doit rester maîtrisée par l'application.
- Le multi-entreprises (lot 4) sera porté par notre propre modèle de données (`tenant_id`, rôles et permissions par entreprise, voir ADR-0002), pas par un IAM externe.
- Le copilot IA (lot 2) nécessite un token délégué dont les permissions sont l'intersection de celles de l'utilisateur et de celles accordées au copilot : cela dépend de notre catalogue de permissions.
- Aucun besoin actuel de SSO avec des fournisseurs d'identité externes.
- Aucun besoin actuel de MFA ou de fédération d'identité avancée.

## Options étudiées

### Option 1 — Utiliser Keycloak

Keycloak serait utilisé comme fournisseur d'identité (Identity Provider) et gérerait l'authentification et les tokens via des standards tels que OAuth 2.0 et OpenID Connect.

#### Avantages

- Solution mature et largement utilisée.
- Support natif d'OpenID Connect et OAuth 2.0.
- Gestion centralisée des utilisateurs et des sessions.
- Support du SSO.
- Possibilité d'intégrer des fournisseurs d'identité externes.
- Fonctionnalités avancées telles que le MFA.
- Permet de déléguer une partie importante de la gestion de l'identité à un composant spécialisé.

#### Inconvénients

- Nécessite une infrastructure supplémentaire à héberger et maintenir.
- Ajoute de la complexité au déploiement et à l'exploitation.
- Nécessite de comprendre le modèle Keycloak : realms, clients, rôles, scopes, flows, etc.
- Les thèmes et la personnalisation des écrans d'authentification ajoutent de la complexité.
- Crée un couplage supplémentaire entre l'application et Keycloak.
- Les rôles et permissions de chaque entreprise vivent dans notre base (ADR-0002) : avec Keycloak, il faudrait les synchroniser ou les dupliquer entre deux systèmes, et émettre nos tokens délégués pour le copilot reviendrait à contourner Keycloak.
- Les mises à jour et la maintenance de Keycloak deviennent une responsabilité supplémentaire.

---

### Option 2 — Gérer l'authentification directement dans l'API

L'API backend gère directement les utilisateurs, les mots de passe, les rôles et les tokens.

Les tokens sont générés et validés par l'application selon les besoins du projet.

#### Avantages

- Architecture plus simple.
- Pas d'infrastructure IAM supplémentaire.
- Pas de dépendance à Keycloak.
- Modèle de données directement adapté aux besoins de l'application.
- Contrôle complet sur le comportement de l'authentification.
- Déploiement et maintenance simplifiés.
- Plus facile à intégrer au modèle actuel de l'application.

#### Inconvénients

- L'équipe doit gérer elle-même la sécurité de l'authentification.
- Les fonctionnalités avancées comme le SSO et le MFA ne sont pas disponibles nativement.
- L'intégration avec des fournisseurs d'identité externes devra être développée si elle devient nécessaire.
- La gestion des comptes, sessions, rôles et mécanismes de récupération doit rester correctement sécurisée.

---

### Option 3 — Utiliser un fournisseur d'identité externe

L'authentification pourrait être déléguée à un fournisseur externe spécialisé dans l'Identity and Access Management.

#### Avantages

- Réduit la responsabilité liée à la gestion de l'authentification.
- Peut fournir rapidement du SSO, du MFA et l'intégration avec différents fournisseurs d'identité.
- Pas d'infrastructure IAM à héberger soi-même.

#### Inconvénients

- Dépendance à un service externe.
- Coûts potentiels liés au fournisseur.
- Données d'identité gérées par un tiers.
- Risque de couplage à une solution propriétaire.
- Migration potentiellement complexe si le fournisseur est remplacé.
- Surdimensionné pour les besoins actuels du projet.

## Décision

Nous choisissons de gérer l'authentification directement dans l'API backend et de ne pas introduire Keycloak, car les besoins actuels du projet ne justifient pas la complexité et le coût opérationnel d'un IAM dédié.

Cette décision pourra être réévaluée si le projet nécessite notamment du SSO d'entreprise, du MFA avancé ou une fédération avec plusieurs fournisseurs d'identité.

## Conséquences

### Conséquences positives

- Architecture plus simple.
- Moins de composants à déployer et à maintenir.
- Pas d'infrastructure Keycloak supplémentaire.
- Pas de configuration de realms, clients ou flows Keycloak.
- Modèle de données adapté directement aux besoins de l'application.
- Meilleur contrôle sur l'intégration entre authentification et logique métier.
- Déploiement plus simple.

### Conséquences négatives

- L'application doit prendre en charge elle-même la sécurité de l'authentification.
- Les fonctionnalités avancées d'IAM ne sont pas disponibles nativement.
- L'implémentation d'un SSO ou d'un MFA nécessitera une évolution de l'architecture.
- Une future intégration avec un fournisseur d'identité externe pourra nécessiter une refonte partielle du système d'authentification.
- Nous acceptons donc de ne pas bénéficier immédiatement des fonctionnalités IAM avancées de Keycloak afin de conserver une architecture proportionnée aux besoins actuels.

## Réévaluation

Cette décision devra être réévaluée si l'un des besoins suivants apparaît :

- besoin de SSO avec une organisation ou un fournisseur d'identité d'entreprise ;
- besoin de fédérer plusieurs fournisseurs d'identité ;
- besoin de MFA avancé ;
- multiplication importante des applications partageant la même authentification ;
- besoin d'une gestion centralisée des identités et des rôles entre plusieurs organisations ;
- augmentation de la complexité du modèle multi-entreprises.
