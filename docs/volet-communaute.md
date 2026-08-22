# Volet Communauté — État des lieux et pistes de continuation

> Document destiné aux étudiant/étudiantes qui reprendront le volet
> communauté de Vignette. Il résume ce qui a été construit, comment ça
> fonctionne, ce qui reste incomplet, et des pistes pour la suite.

Le "volet communauté" désigne ici tout ce qui a été construit pour donner à
Vignette une couche sociale et collaborative : discussions, permissions,
collaboration sur les scénarios, forks/remix, likes, bookmarks, suivi de
langues, notifications temps réel, historique d'activité, et les pages/
panneaux qui les rassemblent (en particulier la page langue,
`LanguageDetailView.vue`, qui sert de hub communautaire). Ce n'est pas
limité au package Java `community`, plusieurs de ces fonctionnalités
vivent dans le code "scénario" ou "langue" du projet, mais font partie du
même effort et du même fil narratif.

Pour retrouver précisément qui a fait quoi et quand :

```bash
git log --author="Ariane" --oneline
git log --author="Ariane" --name-only
```

## 1. Vue d'ensemble des sous-fonctionnalités

| # | Fonctionnalité | En une phrase |
|---|---|---|
| 2.1 | Discussions | Fils de messages threadés attachés à un scénario (langue/audio prévus mais pas branchés en UI). |
| 2.2 | Accréditations | Permissions communautaires scopées (langue, scénario, global) avec flux demande → révision → octroi. |
| 2.3 | Collaboration sur les scénarios | Co-auteurs (rôles OWNER/EDITOR/VIEWER), invitations nominatives, liens d'invitation. |
| 2.4 | Fork / remix | Copier un scénario publié pour le modifier, avec révision par l'auteur original avant republication. |
| 2.5 | Likes & Bookmarks | Aimer/enregistrer un scénario, organiser ses bookmarks par catégories. |
| 2.6 | Suivi de langues | S'abonner à une langue pour être notifié des nouveaux scénarios publiés. |
| 2.7 | Notifications temps réel | Centre de notifications + flux SSE (Server-Sent Events) pour la mise à jour live. |
| 2.8 | Historique de scénario | Journal d'activité (créé, storyboard modifié, média ajouté, publié...) par scénario. |
| 2.9 | Page langue — hub communautaire | `LanguageDetailView.vue` réunit follow, discussions, likes/bookmarks par scénario. |
| 2.10 | Panneaux de gouvernance/admin | Modération globale (`AdminCommunityView`) et par scénario (`ScenarioManageView`). |

## 2. Ce qui a été fait, par fonctionnalité

### 2.1 Discussions (fils de messages)

**Modèle** : `DiscussionMessage` (schéma `community`) — message threadé
(`parentMessageId`), rattaché à une cible via `DiscussionTargetType`
(`LANGUAGE`, `AUDIO`, `SCENARIO`) et catégorisé par `ContributionType`
(`GENERAL`, `TRANSCRIPTION`, `TRANSLATION`, `GLOSS`, `INTERPRETATION`).

**Backend** : [`CommunityService`](../src/main/java/org/titiplex/service/CommunityService.java)
(validation de cible, création, notification du parent en cas de réponse)
+ [`CommunityApiController`](../src/main/java/org/titiplex/api/CommunityApiController.java)
(`GET/POST /api/community/discussions`, lecture publique, écriture
authentifiée).

**Frontend** :
- [`DiscussionThread.vue`](../vite/src/components/community/DiscussionThread.vue) —
  composant réutilisable : affichage en arbre, avatar généré, composer,
  état invité, scroll + flash sur un message précis (`highlightMessageId`).
- [`ScenarioDiscussionModal.vue`](../vite/src/components/community/ScenarioDiscussionModal.vue) —
  ouvre `DiscussionThread` en modale pour un scénario (`targetType:
  SCENARIO`). C'est le seul point d'entrée actif ; utilisé depuis
  `ScenariosView.vue`, `ScenarioDetailView.vue` et `LanguageDetailView.vue`
  (bouton "Discussion" sur chaque scénario listé sous une langue).

**Notifications** : réponse à un message (`COMMENT_REPLY`), nouveau
message sur un scénario notifie l'auteur + les collaborateurs acceptés
(`NEW_COMMENT_ON_SCENARIO`).

**Tests** : `CommunityServiceTest`, `CommunityApiControllerTest`,
`CommunityApiControllerMvcTest`.

### 2.2 Accréditations / permissions communautaires

**Modèles** : `AccreditationRequest` (demande, statut `PENDING/APPROVED/
REJECTED`), `CommunityAccreditation` (octroi effectif), avec
`AccreditationPermissionType` (`COMMUNITY_REVIEW`, `LANGUAGE_EDIT`,
`SCENARIO_EDIT`, `SCENARIO_MODERATE`) et `AccreditationScopeType`
(`GLOBAL`, `SCENARIO`, `LANGUAGE`, `LANGUAGE_FAMILY`).

**Backend** : `CommunityService` valide la cohérence permission/scope
(ex. `SCENARIO_EDIT` seulement en scope `SCENARIO`), empêche les demandes
`PENDING` en double, gère la revue (`reviewRequest`) et l'octroi direct
(`grantAccreditation`, idempotent). `CommunityApiController` expose
`POST /accreditation-requests`, `GET /accreditation-requests`,
`POST /accreditation-requests/{id}/review`, `GET/POST /accreditations`.
Règles d'accès (`canManage`) : `GLOBAL`/`LANGUAGE`/`LANGUAGE_FAMILY` →
admin uniquement ; `SCENARIO` → admin **ou** propriétaire du scénario.

**Frontend** :
- [`AdminCommunityView.vue`](../vite/src/views/AdminCommunityView.vue) —
  panneau admin (`/admin/community`), scope `GLOBAL` uniquement.
- `ScenarioManageView.vue` — panneau équivalent scopé à un scénario
  (revue des demandes, octroi direct, liste des accréditations en cours).

### 2.3 Collaboration sur les scénarios

**Modèles** : `ScenarioCollaborator` (rôle `OWNER/EDITOR/VIEWER`, statut
`PENDING/ACCEPTED/DECLINED/EXPIRED`, `expiresAt`), `ScenarioInviteLink`
(token unique, rôle, `maxUses`/`useCount`, `active`, `expiresAt`).

**Backend** : [`ScenarioCollaborationService`](../src/main/java/org/titiplex/service/ScenarioCollaborationService.java)
(364 lignes) — inviter par nom d'utilisateur, répondre à une invitation,
retirer un collaborateur, changer un rôle, créer/lister/révoquer un lien
d'invitation, rejoindre via un lien (`joinViaLink`). Exposé par
`ScenarioCollaborationApiController` (`/api/scenarios/{id}/collaborators`,
`/api/invite-links/...`).

**Frontend** :
- `components/community/CollaboratorsPanel.vue` + `composables/
  useCollaborators.js` — gestion des collaborateurs depuis la page de
  gestion d'un scénario.
- `views/InvitationsView.vue` — invitations de collaboration en attente
  pour l'utilisateur connecté.
- `views/InviteJoinView.vue` — page d'atterrissage pour rejoindre un
  scénario via un lien d'invitation.

**Notifications** : `COLLABORATION_INVITE`, `COLLABORATION_ACCEPTED`.

**Tests** : `ScenarioCollaborationServiceTest`.

### 2.4 Fork / remix de scénarios (avec révision)

Un utilisateur peut forker (copier) un scénario **publié** pour le
modifier de son côté (`ScenarioService.forkScenario`) — le fork est créé
en `DRAFT`, rattaché à l'original via `parentScenarioId`, avec un
`ReviewStatus.PENDING`. **Republier ce fork nécessite l'approbation de
l'auteur original** (`ScenarioService.reviewFork` /
`ScenarioApiController`) — sans quoi la publication est bloquée
(`AccessDeniedException`).

**Frontend** : `components/scenario/CopyScenarioModal.vue`,
`api/dto/ForkScenarioRequest`.

**Notifications** : `FORK_REVIEW_REQUESTED` (à l'auteur original),
`FORK_APPROVED` / `FORK_REJECTED` (à l'auteur du fork).

**Tests** : `ScenarioServiceForkTest`.

### 2.5 Likes & Bookmarks

**Modèles** : `ScenarioLike`, `ScenarioBookmark` (contrainte unique
scénario+utilisateur).

**Backend** : [`ScenarioInteractionService`](../src/main/java/org/titiplex/service/ScenarioInteractionService.java)
(like/unlike, bookmark/unbookmark, statut par scénario, "mes
interactions") exposé par `ScenarioInteractionApiController`
(`POST/DELETE /api/scenarios/{id}/like`, `.../bookmark`,
`GET .../interactions`, `GET /api/scenarios/interactions/mine`).

**Frontend** :
- `composables/useScenarioInteractions.js` — état optimiste en
  `localStorage` (cache pour l'UI instantanée) **re-synchronisé** avec le
  backend via `/interactions/mine` (le commentaire dans le code est
  explicite : *"localStorage is only an optimistic-UI cache: it can
  drift"*).
- `views/LikedScenariosView.vue`, `views/BookmarkedScenariosView.vue` —
  pages dédiées.
- `composables/useBookmarkCategories.js` — catégories personnalisées de
  bookmarks (ex. "To study", "Field research"), avec suggestions par
  défaut. **Stocké uniquement en `localStorage`** — le commentaire en
  tête de fichier le dit lui-même : *"Gestion des catégories de bookmarks
  — localStorage, backend plus tard"*.

### 2.6 Suivi de langues (Language follows)

**Modèle** : `LanguageFollow` (contrainte unique langue+utilisateur).

**Backend** : `LanguageFollowService` (toggle, statut, liste des langues
suivies, liste/compte des followers d'une langue) +
`LanguageFollowController`. Contrairement aux likes/bookmarks, **la
source de vérité est uniquement le backend** — le commentaire en tête de
`useLanguageFollows.js` le précise : *"Source de vérité : backend
uniquement — plus de localStorage"*.

**Notification** : quand un nouveau scénario est publié dans une langue
suivie, tous les followers (sauf l'auteur) reçoivent `NEW_SCENARIO_
IN_FOLLOWED_LANGUAGE`, avec déduplication (`existsByUserIdAndTypeAnd
ReferenceId`).

### 2.7 Notifications temps réel

**Modèle** : `Notification` (type, message, `targetUrl`, `referenceId`,
`read`).

**Backend** : [`NotificationService`](../src/main/java/org/titiplex/service/NotificationService.java)
(430 lignes) centralise **tous** les types de notifications du volet
communauté : `NEW_SCENARIO_IN_FOLLOWED_LANGUAGE`, `FORK_REVIEW_REQUESTED`,
`FORK_APPROVED`, `FORK_REJECTED`, `COLLABORATION_INVITE`,
`COLLABORATION_ACCEPTED`, `COMMENT_REPLY`, `NEW_COMMENT_ON_SCENARIO`.
Poussées en temps réel via SSE (`pushToUser`). `NotificationController`
expose `GET /stream` (flux SSE), `GET` (liste), `POST /{id}/read`,
`POST /read-all`, `DELETE /{id}`, `DELETE` (tout effacer).

**Frontend** :
- `composables/useSSE.js` — connexion `EventSource` avec reconnexion
  automatique et backoff exponentiel (2s → 30s max).
- `composables/useNotifications.js` — état des notifications, consommé
  par la cloche dans `components/AppHeader.vue`.

**Tests** : `NotificationServiceTest`.

### 2.8 Historique de scénario (journal d'activité)

**Modèle** : `ScenarioHistoryEntry` (acteur, action, résumé, horodatage),
`ScenarioHistoryAction` (`SCENARIO_CREATED`, `METADATA_UPDATED`,
`STORYBOARD_UPDATED`, `THUMBNAIL_ADDED/UPDATED/DELETED`,
`AUDIO_ADDED/UPDATED/DELETED`, `PUBLISHED`).

**Backend** : `ScenarioHistoryService` (`record`, `list`) — volontairement
minimal (36 lignes), appelé depuis `ScenarioService` à chaque action
notable.

**Frontend** : `components/scenario/ScenarioHistoryPanel.vue` — affiché
dans la gestion d'un scénario, pour que les collaborateurs voient qui a
fait quoi (utile en collaboration multi-auteurs).

### 2.9 La page langue comme hub communautaire — `LanguageDetailView.vue`

C'est **la page communautaire des langues** : elle ne se contente pas
d'afficher les scénarios d'une langue, elle rassemble en un seul endroit
la plupart des interactions communautaires disponibles pour cette langue :

- bouton **Follow/Unfollow** (`useLanguageFollows`) avec état visuel actif ;
- **like/bookmark** par scénario listé, avec compteurs (`likeCounts`) et
  attribution de **catégorie de bookmark** directement depuis la carte du
  scénario (`useScenarioInteractions`, `useBookmarkCategories`) ;
- bouton **Discussion** par scénario, qui ouvre `ScenarioDiscussionModal` ;
- recherche/filtre par tag, tri, vue grille ou lecture "single" scénario
  par scénario (`ScenarioReaderModal`).

C'est le fichier le plus dense du volet communauté côté front (plusieurs
centaines de lignes).

### 2.10 Panneaux de gouvernance / admin

- `AdminCommunityView.vue` — modération globale (accréditations `GLOBAL`).
- `AdminUsersView.vue` — gestion des comptes (touché par les mêmes
  correctifs que le reste du volet, à vérifier si des droits communautaires
  y sont exposés).
- `ScenarioManageView.vue` — la vue de gestion privée d'un scénario réunit
  aussi : `CollaboratorsPanel`, gouvernance d'accréditation scopée
  `SCENARIO`, et `ScenarioHistoryPanel`. C'est le pendant "par scénario"
  de `LanguageDetailView.vue`.

## 3. État actuel — ce qui manque ou reste incomplet

1. **Discussions Langue et Audio non branchées côté front.**
   `DiscussionTargetType.LANGUAGE`/`AUDIO` sont gérés par
   `CommunityService.validateTarget`, mais aucune vue n'instancie
   `DiscussionThread` avec ces cibles. Seules les discussions de type
   `SCENARIO` sont accessibles (y compris depuis la page langue, mais
   toujours **au niveau du scénario**, jamais de la langue elle-même).
2. **Le type de contribution (`ContributionType`) n'est jamais choisi par
   l'utilisateur.** `CONTRIBUTION_TYPES` est exporté par `api/community.js`
   mais `DiscussionThread.vue` envoie toujours `contributionType:
   "GENERAL"` en dur — pas de `<select>` dans le composer.
3. **Aucune UI pour qu'un utilisateur *demande* une accréditation.**
   `createAccreditationRequest` (`api/community.js`) n'est appelé nulle
   part dans le front. Il n'y a donc pas de bouton "Devenir éditeur de
   cette langue" / "Demander à modérer ce scénario" visible par un
   utilisateur normal — seules les interfaces de *revue* et d'*octroi
   direct* existent.
4. **`AdminCommunityView` ne couvre que le scope `GLOBAL`.** Pas d'écran
   admin pour gérer les accréditations `LANGUAGE`/`LANGUAGE_FAMILY`.
5. **Catégories de bookmarks 100% côté client.** `useBookmarkCategories.js`
   stocke tout en `localStorage` (le code le documente lui-même comme
   temporaire) : perdu si le cache navigateur est vidé, non synchronisé
   entre appareils/navigateurs. Les likes/bookmarks eux-mêmes sont bien
   côté serveur ; seules les *catégories* ne le sont pas.
6. **Pas de révocation d'accréditation** (pas d'endpoint `DELETE` pour
   `CommunityAccreditation`) — une fois accordée, une accréditation ne
   peut pas être retirée via l'API.
7. **Pas d'édition/suppression de message de discussion**, ni de
   signalement ("report") — donc pas de modération de contenu abusif au
   niveau API en dehors d'une intervention en base.
8. **Pas de pagination** sur les discussions, les accréditations, les
   demandes, ni sur l'historique de scénario — correct au volume actuel,
   à surveiller si ça grossit.
9. **Pas de tests end-to-end (Playwright)** sur les parcours communauté.
   Les specs `studio-flow.spec.js`/`scenario-playback.spec.js`
   interceptent juste `/api/community/accreditation-requests` pour éviter
   des erreurs réseau — aucun scénario e2e ne teste réellement poster un
   message, inviter un collaborateur, forker puis faire approuver, etc.
   La couverture réelle est côté backend uniquement.
10. **Pas d'anti-spam / rate limiting** sur la création de messages, de
    demandes d'accréditation, ou d'invitations.

## 4. Pistes de continuation

### Rapide à faire, impact direct
- Ajouter un `<select>` de `ContributionType` dans `DiscussionThread.vue`
  (la liste existe déjà côté API).
- Brancher `DiscussionThread` sur `LanguageDetailView.vue` pour une
  discussion **au niveau de la langue** elle-même (pas juste ses
  scénarios).
- Migrer `useBookmarkCategories.js` du `localStorage` vers un vrai
  endpoint backend (modèle simple : table `bookmark_category` +
  `scenario_bookmark.category`), pour que les catégories survivent au
  nettoyage du cache et se synchronisent entre appareils.

### Effort moyen
- **Construire le flux de demande d'accréditation côté utilisateur** :
  bouton/formulaire (sur `LanguageDetailView` ou `ScenarioDetailView`) qui
  appelle `createAccreditationRequest`, déjà prêt côté API — c'est le
  chaînon manquant le plus visible du système d'accréditation.
- Étendre `AdminCommunityView` avec un sélecteur de scope
  (`LANGUAGE`/`LANGUAGE_FAMILY`) + champ `targetId`.
- Discussion sur les clips audio (`targetType="AUDIO"`), à accrocher
  probablement dans le composant de lecture audio.
- Tests e2e Playwright sur : poster/répondre à un message, inviter un
  collaborateur et accepter, forker un scénario et le faire approuver,
  suivre une langue et recevoir une notification.

### Plus structurant
- Révocation d'accréditation (`DELETE /api/community/accreditations/{id}`)
  + UI correspondante.
- Modération des messages (édition/suppression par auteur/modérateur,
  signalement).
- Préférences de notification par utilisateur (actuellement tout est
  notifié sans opt-out).
- Tableau de bord communauté : contributeurs actifs, messages par langue/
  scénario, temps de traitement des demandes, langues les plus suivies.
- Pagination + recherche dans les fils de discussion et listes de
  gouvernance, une fois le volume réel constaté.

## 5. Carte des fichiers

| Besoin | Fichier(s) |
|---|---|
| Discussions — modèle & logique | `persistence/model/{DiscussionMessage,DiscussionTargetType,ContributionType}.java`, `service/CommunityService.java` |
| Accréditations — modèle & logique | `persistence/model/{AccreditationRequest,CommunityAccreditation,AccreditationPermissionType,AccreditationScopeType,AccreditationRequestStatus}.java`, `service/CommunityService.java` |
| API communauté (discussions + accréditations) | `api/CommunityApiController.java` |
| Collaboration scénario | `persistence/model/{ScenarioCollaborator,ScenarioInviteLink,CollaboratorRole,CollaborationStatus}.java`, `service/ScenarioCollaborationService.java`, `api/ScenarioCollaborationApiController.java` |
| Fork / remix | `service/ScenarioService.java` (`forkScenario`, `reviewFork`), `persistence/model/ReviewStatus.java` |
| Likes / bookmarks | `persistence/model/{ScenarioLike,ScenarioBookmark}.java`, `service/ScenarioInteractionService.java`, `api/ScenarioInteractionApiController.java` |
| Suivi de langues | `persistence/model/LanguageFollow.java`, `service/LanguageFollowService.java`, `api/LanguageFollowController.java` |
| Notifications | `persistence/model/Notification.java`, `service/NotificationService.java`, `api/NotificationController.java` |
| Historique de scénario | `persistence/model/{ScenarioHistoryEntry,ScenarioHistoryAction}.java`, `service/ScenarioHistoryService.java` |
| Fil de discussion (composant) | `vite/src/components/community/DiscussionThread.vue` |
| Discussion scénario (modale) | `vite/src/components/community/ScenarioDiscussionModal.vue` |
| Collaborateurs (panneau) | `vite/src/components/community/CollaboratorsPanel.vue`, `vite/src/composables/useCollaborators.js` |
| Invitations | `vite/src/views/{InvitationsView,InviteJoinView}.vue` |
| Likes/bookmarks (état + pages) | `vite/src/composables/{useScenarioInteractions,useBookmarkCategories}.js`, `vite/src/views/{LikedScenariosView,BookmarkedScenariosView}.vue` |
| Suivi de langues (état) | `vite/src/composables/useLanguageFollows.js` |
| Notifications (temps réel) | `vite/src/composables/{useNotifications,useSSE}.js`, `vite/src/components/AppHeader.vue` |
| Historique (panneau) | `vite/src/components/scenario/ScenarioHistoryPanel.vue` |
| Hub communautaire langue | `vite/src/views/LanguageDetailView.vue` |
| Gouvernance par scénario | `vite/src/views/ScenarioManageView.vue` |
| Gouvernance globale (admin) | `vite/src/views/AdminCommunityView.vue` |
| Client API communauté | `vite/src/api/community.js` |
| Tests backend | `src/test/java/org/titiplex/{api,service}/{Community*,ScenarioCollaboration*,ScenarioServiceFork*,NotificationService}Test.java` |

## 6. Pour se lancer

1. Lire le [README](../README.md) et lancer le projet en profil `pgdev`.
2. Se connecter avec `admin` / `admin12345` pour voir `/admin/community`.
3. Ouvrir une page langue (`/languages/:id`) — c'est le meilleur endroit
   pour voir converger follow, likes, bookmarks et discussion en situation
   réelle.
4. Explorer `/api/docs` (Swagger) → tags **Community**, pour tester les
   endpoints avant de brancher une nouvelle vue.

## 7. Historique / contact

Voir [CONTRIBUTORS.md](../CONTRIBUTORS.md), et le
[rapport de projet](https://protolabo.github.io/vignette/) pour le
contexte académique global. Pour l'historique détaillé du volet
communauté (commit par commit), voir la section 1 de ce document.
