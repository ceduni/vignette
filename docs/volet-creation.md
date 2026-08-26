# Volet création : guide de reprise du Studio et du Vignette Maker

## Introduction

Ce document présente le volet création de Vignette dans son état actuel. Il s’adresse aux prochaines personnes qui devront comprendre, maintenir ou faire évoluer le Studio, les brouillons audio, l’enregistrement d’urgence, le Vignette Maker, le storyboard, les ambiances, le lecteur et la publication des scénarios.

Le but n’est pas de commenter chaque ligne du projet. Le but est de montrer comment une action de l’utilisateur traverse l’application, où les données sont conservées, quels fichiers participent à chaque fonctionnalité et quels tests doivent être exécutés après une modification.

Il est possible de travailler sur une petite partie sans maîtriser tout le projet. Une personne qui veut seulement modifier le bouton d’enregistrement d’urgence peut aller directement à la section 6. Une personne qui veut ajouter un outil au Studio devrait d’abord lire les sections 3, 8 et 20.

## Table des matières

1. Démarrage rapide
2. Technologies utilisées
3. Architecture générale
4. Navigation entre les pages
5. Espace personnel et création d’un scénario
6. Enregistrement d’urgence et brouillons locaux
7. Passage d’un brouillon audio vers le Studio
8. Organisation interne du Studio
9. Images et scènes du storyboard
10. Déplacement, ordre et dimensions des images
11. Vignette Maker
12. Enregistrements et prises audio
13. Importation et conversion des fichiers audio
14. Découpage audio
15. Ambiances du scénario
16. Lecteur et lecture automatique
17. Publication et modifications après publication
18. Backend, API et modèles de données
19. Stockage des fichiers et sécurité
20. Carte des fichiers
21. Tests
22. Dépannage
23. Limites actuelles et pistes de continuation
24. Glossaire
25. Liste de vérification avant une contribution

## 1. Démarrage rapide

### 1.1 Prérequis

Le projet utilise Java 21 ou une version plus récente, Maven, Node.js, npm et un navigateur moderne. PostgreSQL est nécessaire pour l’environnement complet. Le profil `dev` permet de travailler rapidement avec une base H2 locale.

### 1.2 Lancer le backend léger

Depuis la racine du projet :

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Le backend est ensuite disponible sur `http://localhost:8081`.

La documentation interactive de l’API est disponible sur :

```text
http://localhost:8081/api/docs
```

### 1.3 Lancer le frontend

Dans un autre terminal :

```bash
cd vite
npm install
npm run dev
```

Le frontend est généralement disponible sur `http://localhost:5173`.

Le serveur Vite redirige les requêtes commençant par `/api` vers le backend sur le port 8081. Cette configuration se trouve dans `vite/vite.config.js`.

### 1.4 Pages utiles

```text
/workspace             Espace personnel et brouillons
/create-scenario       Création des métadonnées d’un scénario
/scenarios/:id         Studio et page détaillée du scénario
/scenarios             Scénarios publiés
```

### 1.5 Première lecture conseillée

Pour comprendre rapidement le volet création, ouvrir les fichiers suivants dans cet ordre :

1. `vite/src/router/index.js`
2. `vite/src/views/WorkspaceHomeView.vue`
3. `vite/src/views/CreateScenarioView.vue`
4. `vite/src/views/ScenarioDetailView.vue`
5. `vite/src/api/scenarios.js`
6. `src/main/java/org/titiplex/api/ScenarioApiController.java`
7. `src/main/java/org/titiplex/service/ScenarioService.java`

## 2. Technologies utilisées

### 2.1 Vue 3

Vue construit l’interface à partir de composants. Un fichier `.vue` contient généralement une partie `<script setup>`, une partie `<template>` et une partie `<style scoped>`.

La partie script contient l’état et les actions. La partie template décrit ce qui est affiché. La partie style définit l’apparence du composant.

Le volet création utilise principalement l’API de composition de Vue.

`ref` crée une valeur réactive :

```js
const loading = ref(false);
```

Pour lire ou modifier cette valeur dans le script, il faut utiliser `loading.value`. Dans le template, Vue enlève automatiquement `.value`.

`computed` calcule une valeur à partir d’autres valeurs réactives :

```js
const sortedThumbnails = computed(() => sortByIdxThenId(thumbnails.value));
```

`watch` exécute une action lorsqu’une valeur change. Il est utilisé, par exemple, pour relancer l’ambiance lorsque la sélection change.

`onMounted` exécute une fonction lorsque le composant apparaît. `onBeforeUnmount` et `onUnmounted` servent à arrêter les lecteurs audio, retirer les écouteurs et libérer les URL temporaires.

### 2.2 Composables Vue

Un composable est une fonction JavaScript dont le nom commence normalement par `use`. Il regroupe une logique réutilisable et expose l’état ainsi que les fonctions nécessaires à l’interface.

Exemple simplifié :

```js
export function useThumbnailLifecycle(options = {}) {
  const selectThumb = (thumb) => {
    options.selectedThumb.value = thumb;
  };

  return {selectThumb};
}
```

Dans le Studio, les composables empêchent `ScenarioDetailView.vue` de contenir toute la logique dans un seul fichier. Lorsqu’une nouvelle fonctionnalité possède son propre état, ses propres règles et plusieurs actions, il est préférable de créer un composable plutôt que d’ajouter toute la logique directement dans la vue.

### 2.3 Vue Router

Vue Router associe une URL à un composant. Les paramètres de route sont accessibles avec `useRoute`. La navigation programmée utilise `useRouter`.

Exemple :

```js
const route = useRoute();
const router = useRouter();

const draftId = route.query.draftAudio;
router.push(`/scenarios/${scenarioId}`);
```

### 2.4 Vite

Vite fournit le serveur de développement, transforme les composants Vue et produit les fichiers de production. Le build se lance avec :

```bash
cd vite
npm run build
```

### 2.5 API du navigateur

Le volet création utilise plusieurs fonctions intégrées au navigateur.

`navigator.mediaDevices.getUserMedia` demande l’accès au microphone.

`MediaRecorder` enregistre le flux du microphone.

`Blob` représente des données binaires en mémoire.

`File` ajoute à un Blob un nom et un type MIME adaptés à un envoi.

`FileReader` convertit un Blob en Data URL pour le stockage local.

`FormData` envoie des champs et un fichier dans une requête multipart.

`AudioContext` décode les formats audio et permet de produire un WAV.

`localStorage` conserve de petites données dans le navigateur.

Canvas dessine les arrière plans du Vignette Maker.

SVG dessine les personnages, les bulles et leurs contrôles.

### 2.6 Spring Boot

Le backend Spring est divisé en couches.

Le contrôleur reçoit la requête HTTP.

Le service applique les règles du projet.

Le repository lit et écrit dans la base de données.

Le modèle JPA représente une table.

Le DTO représente la forme envoyée au frontend.

### 2.7 JPA, H2, PostgreSQL et Flyway

JPA relie les modèles Java aux tables. Le profil `dev` utilise H2 avec `ddl-auto=update`. Le profil `pgdev` utilise PostgreSQL avec la mise à jour du schéma par Hibernate. Le profil de production utilise Flyway et demande à Hibernate de valider le schéma.

Les migrations du volet création sont notamment :

```text
V12__scenario_background_audio.sql
V13__vignette_maker_scenes.sql
V14__active_scenario_background_audio.sql
```

### 2.8 Vitest et Playwright

Vitest exécute les tests unitaires du frontend dans un environnement simulé avec jsdom.

Playwright ouvre un vrai navigateur et vérifie des parcours complets.

JUnit, Mockito et Spring MockMvc vérifient le backend.

## 3. Architecture générale

### 3.1 Trajet d’une action persistée

Lorsqu’un utilisateur ajoute une image, l’action suit ce trajet :

```text
Bouton du Studio
    ↓
ScenarioDetailView.vue
    ↓
useSceneImageUpload.js
    ↓
scenarios.js
    ↓
ThumbnailApiController.java
    ↓
ThumbnailService.java
    ↓
ThumbnailRepository.java et FileStorageService.java
    ↓
Base de données et data/storage
```

La même structure est utilisée pour les scénarios et les audios, avec les contrôleurs et services correspondants.

### 3.2 Trajet d’une action locale

Un enregistrement d’urgence n’est pas immédiatement envoyé au backend.

```text
Microphone
    ↓
MediaRecorder
    ↓
Blob audio
    ↓
Data URL
    ↓
Objet JSON
    ↓
localStorage
```

Il devient un vrai média du scénario seulement lorsqu’il est importé dans une scène persistée.

### 3.3 Séparation des données et des médias

La base ne contient pas directement tout le contenu des images et des audios. Elle conserve leurs métadonnées et leur chemin.

Exemple pour une image :

```text
thumbnail.id
thumbnail.scenarioId
thumbnail.title
thumbnail.idx
thumbnail.storagePath
thumbnail.contentType
thumbnail.imageSha256
thumbnail.sizeBytes
```

Le fichier lui même se trouve sous le dossier de stockage. Cette séparation évite de placer de gros fichiers binaires dans les lignes de la base.

### 3.4 Formats utilisés

JSON sert aux métadonnées, aux requêtes normales et à l’état éditable du Vignette Maker.

FormData sert aux images et aux audios envoyés au backend.

Blob représente un média temporaire dans le navigateur.

Data URL contient un petit média encodé en texte, principalement pour les brouillons audio locaux.

Object URL fournit une URL temporaire commençant par `blob:` pour écouter ou afficher un Blob.

SVG décrit les personnages du Vignette Maker.

Canvas dessine les arrière plans actuels.

PNG est le format exporté par le Vignette Maker avant l’ajout au storyboard.

WAV PCM est utilisé lorsqu’un audio est converti ou découpé dans le navigateur.

## 4. Navigation entre les pages

### 4.1 Déclaration des routes

Les routes sont définies dans `vite/src/router/index.js`.

Les pages privées utilisent :

```js
meta: {requiresAuth: true}
```

Avant chaque navigation, le routeur charge l’utilisateur si nécessaire. Il redirige ensuite vers la page de connexion si la route demande une authentification.

### 4.2 Recréation du composant lorsque l’URL change

Le fichier `vite/src/App.vue` affiche la page courante.

```vue
<RouterView v-slot="{ Component, route }">
  <component :is="Component" :key="route.fullPath"/>
</RouterView>
```

La clé utilise `route.fullPath`, donc le chemin complet avec ses paramètres. Lorsque le même composant est ouvert avec un autre identifiant ou un autre brouillon audio, Vue recrée la page et relance son chargement.

Cette ligne est importante pour le parcours suivant :

```text
/create-scenario?draftAudio=draft-123
    ↓
/scenarios/42?draftAudio=draft-123
```

### 4.3 AppShell

`vite/src/layouts/AppShell.vue` affiche l’en tête, la page, les notifications visuelles, l’enregistreur d’urgence et le pied de page.

Le bouton d’urgence est masqué dans le Studio :

```js
const isStudio = computed(() => route.name === "scenario-detail");
```

Cette règle évite d’afficher deux outils d’enregistrement différents au même moment.

## 5. Espace personnel et création d’un scénario

### 5.1 WorkspaceHomeView

`vite/src/views/WorkspaceHomeView.vue` représente la page Mes scénarios.

Elle charge :

1. les scénarios appartenant à l’utilisateur;
2. les scénarios partagés avec lui;
3. les aperçus des scènes;
4. les brouillons audio stockés dans son navigateur.

La vue gère également la recherche, les filtres, la modification des métadonnées, la suppression d’un scénario et la lecture rapide d’un brouillon.

L’événement personnalisé suivant synchronise l’enregistreur flottant et l’espace personnel :

```js
window.dispatchEvent(
  new CustomEvent("vignette:draft-audios-changed", {detail: next})
);
```

`WorkspaceHomeView.vue` écoute cet événement et actualise la liste sans recharger la page.

### 5.2 CreateScenarioView

`vite/src/views/CreateScenarioView.vue` collecte :

```text
title
description
languageId
tags
audience
allowComments
allowTranscription
allowDownload
```

Le titre et la langue sont obligatoires dans l’interface. La recherche de langue utilise un délai afin d’éviter une requête à chaque caractère saisi.

La création transmet actuellement tous ces champs au client API. Le contrat backend `CreateScenarioRequest` conserve toutefois seulement les quatre champs suivants :

```text
title
description
languageId
tags
```

Les choix `audience`, `allowComments`, `allowTranscription` et `allowDownload` préparent donc une évolution de la page, mais ils ne sont pas encore stockés dans la base. Un nouveau scénario possède le statut persistant `DRAFT`. L’action de publication le fait passer à `PUBLIC`.

Le client `vite/src/api/scenarios.js` envoie la création avec :

```http
POST /api/scenarios
Content-Type: application/json
```

Après la réponse, la vue redirige vers le Studio correspondant.

### 5.3 Création depuis un brouillon audio

Si l’URL contient `draftAudio` et `draftTitle`, le titre du brouillon préremplit le formulaire. Après la création du scénario, les paramètres sont conservés dans la nouvelle URL afin que le Studio sache quel brouillon importer.

## 6. Enregistrement d’urgence et brouillons locaux

### 6.1 Rôle de l’enregistreur

`vite/src/components/EmergencyAudioRecorder.vue` permet d’enregistrer immédiatement une idée sans créer d’abord un scénario.

L’outil doit rester évident, rapide et indépendant du backend. Une panne temporaire du serveur ne doit pas empêcher la capture du brouillon.

### 6.2 Demande du microphone

La fonction `ensureRecorder` demande un flux audio :

```js
stream = await navigator.mediaDevices.getUserMedia({audio: true});
recorder = new MediaRecorder(stream);
```

Le navigateur affiche sa propre demande d’autorisation. Si l’utilisateur refuse, le composant transforme les erreurs techniques en messages compréhensibles.

Les erreurs reconnues incluent :

```text
NotAllowedError
SecurityError
NotFoundError
NotReadableError
```

### 6.3 Accumulation des données

`MediaRecorder` produit plusieurs morceaux pendant l’enregistrement.

```js
recorder.ondataavailable = (event) => {
  if (event.data?.size > 0) chunks.push(event.data);
};
```

Lorsque l’utilisateur arrête :

```js
const blob = new Blob(chunks, {
  type: recorder.mimeType || "audio/webm"
});
```

Le type exact dépend du navigateur. WebM avec Opus est fréquent dans Chromium. D’autres navigateurs peuvent produire OGG ou MP4.

### 6.4 Conversion en Data URL

Un Blob ne peut pas être écrit directement dans `localStorage`. Le composant utilise `FileReader` :

```js
function blobToDataUrl(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}
```

La valeur obtenue commence par un en tête semblable à :

```text
data:audio/webm;base64,GkXfo59ChoEBQveBA...
```

### 6.5 Format du brouillon

```json
{
  "id": "draft-1787712345678",
  "title": "Draft 1",
  "createdAt": "2026-08-26T20:00:00.000Z",
  "mimeType": "audio/webm",
  "dataUrl": "data:audio/webm;base64,..."
}
```

L’identifiant est local. Il ne correspond pas à une ligne de la table `audio`.

### 6.6 Clés localStorage

Les clés sont construites dans `vite/src/utils/draftAudioStorage.js`.

```js
const BASE_KEY = "vignette:unclaimed-draft-audios";
```

Pour une personne non connectée :

```text
vignette:unclaimed-draft-audios:anonymous
```

Pour une personne connectée :

```text
vignette:unclaimed-draft-audios:nomUtilisateur
```

### 6.7 Migration des anciens brouillons

`migrateLegacyDraftAudios` déplace une ancienne clé globale vers la clé anonyme.

`migrateAnonymousDraftAudios` déplace les brouillons anonymes vers la clé de l’utilisateur après sa connexion.

La fusion vérifie les identifiants pour éviter de dupliquer un brouillon déjà présent.

### 6.8 Limites du stockage local

Les brouillons ne sont disponibles que dans le navigateur où ils ont été créés. Ils ne sont pas synchronisés entre deux ordinateurs.

Le Base64 prend environ un tiers d’espace supplémentaire par rapport au fichier binaire. Un long enregistrement peut remplir rapidement le quota de `localStorage`.

Si le stockage est plein, le composant garde temporairement l’enregistrement en mémoire et affiche un avertissement. Un rechargement peut alors faire perdre ce brouillon.

## 7. Passage d’un brouillon audio vers le Studio

### 7.1 Construction du chemin

`draftAudioStudioPath` choisit le parcours selon l’état de connexion.

```js
export function draftAudioStudioPath(draft, username) {
  const params = new URLSearchParams({draftAudio: String(draft.id)});

  if (username) {
    if (draft.title) params.set("draftTitle", draft.title);
    return `/create-scenario?${params.toString()}`;
  }

  return `/scenarios/emergency-${draft.id}?${params.toString()}`;
}
```

Une personne connectée crée d’abord un vrai scénario.

Une personne non connectée ouvre un Studio temporaire. Si elle se connecte ensuite, le parcours revient vers la création d’un scénario persistant.

### 7.2 Lecture du brouillon

`vite/src/composables/useDraftAudioImport.js` relit le tableau JSON correspondant à l’utilisateur et cherche l’identifiant demandé.

### 7.3 Data URL vers File

Pour envoyer le brouillon au backend, `draftAudioFile` sépare l’en tête et le contenu Base64.

```text
Data URL
    ↓
atob
    ↓
Uint8Array
    ↓
File
```

Le type MIME détermine l’extension proposée :

```text
audio/ogg          .ogg
audio/mp4          .m4a
audio/aac          .m4a
audio/wav          .wav
autre              .webm
```

### 7.4 Cas sans image

Un audio doit appartenir à une scène. Si le scénario ne contient aucune image, `applyUnclaimedDraftAudio` conserve l’identifiant dans `pendingRouteDraftId` puis ouvre la boîte d’ajout d’image.

Après l’ajout de la première image, `useSceneImageUpload` appelle de nouveau `applyUnclaimedDraftAudio` avec cette nouvelle scène comme destination.

Ce comportement explique pourquoi le Studio ouvert depuis un brouillon demande d’abord de choisir ou créer une image.

### 7.5 Prévention des doublons

Les audios importés localement peuvent conserver `sourceDraftId`. Avant une nouvelle importation, le composable cherche un audio possédant déjà le même identifiant de brouillon.

Si un doublon est trouvé, le Studio sélectionne la prise existante au lieu d’en créer une autre.

## 8. Organisation interne du Studio

### 8.1 Vue principale

`vite/src/views/ScenarioDetailView.vue` est la vue centrale. Elle affiche le Studio pour les personnes qui peuvent modifier le scénario et le storyboard global ou le lecteur pour les autres personnes.

Elle orchestre plusieurs groupes de données :

```js
const scenario = ref(null);
const thumbnails = ref([]);
const audioMap = ref({});
const selectedThumb = ref(null);
const activeAudioId = ref(null);
const backgroundAudios = ref([]);
```

`audioMap` est un objet dont chaque clé est l’identifiant d’une image :

```json
{
  "41": [
    {"id": 81, "title": "Take A", "idx": 1},
    {"id": 82, "title": "Take B", "idx": 2}
  ],
  "42": [
    {"id": 83, "title": "Narration", "idx": 1}
  ]
}
```

### 8.2 Composables branchés au Studio

`useVoiceTakes` réunit la sélection des prises, l’enregistrement rapide, l’import de fichiers et l’import des brouillons.

`useThumbnailLifecycle` gère la sélection, le titre, la suppression et l’ordre des images.

`useThumbnailResize` gère le redimensionnement.

`useThumbnailDragReorder` gère le déplacement visuel.

`useSceneImageUpload` gère l’import d’images et la réception du PNG du Vignette Maker.

`useBackgroundAmbience` gère les ambiances.

`useAudioTrimEditor` gère le découpage.

`useAudioGloss` gère les informations linguistiques attachées à une prise.

`useScenarioAutoplay` joue les prises dans l’ordre.

### 8.3 Chargement initial

La fonction `loadAll` suit cet ordre :

1. charger l’utilisateur;
2. détecter un identifiant commençant par `emergency-`;
3. charger le scénario;
4. déterminer les permissions;
5. charger les images et les prises;
6. charger les ambiances;
7. vérifier certaines demandes liées au scénario;
8. importer le brouillon indiqué dans l’URL;
9. ouvrir une discussion ciblée si l’URL le demande.

Les images et les prises sont chargées avec plusieurs requêtes parallèles. Une erreur sur les audios d’une image ne doit pas empêcher l’affichage des autres scènes.

### 8.4 Studio temporaire

`studioSandboxMode` permet de construire un scénario temporaire sans backend.

Les identifiants locaux commencent notamment par :

```text
local-
background-local-
emergency-
```

Les fonctions de persistance vérifient ces identifiants avant d’appeler l’API. Cette distinction est essentielle. Un identifiant local envoyé comme identifiant numérique au backend produit une erreur.

### 8.5 Wrappers de persistance

La vue importe les fonctions API sous des noms comme `apiDeleteAudio`, puis déclare des fonctions locales du même domaine.

Ces fonctions locales appellent l’API et exécutent ensuite des actions supplémentaires, par exemple `markEditedIfPublished`.

Lorsqu’une nouvelle opération modifie le contenu d’un scénario publié, elle doit passer par un wrapper qui marque la publication comme modifiée.

## 9. Images et scènes du storyboard

### 9.1 Ajout d’une image

Le composable `vite/src/composables/useSceneImageUpload.js` accepte les types courants suivants :

```text
PNG
JPEG
WebP
GIF
SVG
XML
```

Le Vignette Maker fournit directement un Blob PNG.

### 9.2 Aperçu local

Lorsqu’un fichier est choisi, le composable crée une Object URL :

```js
const previewUrl = URL.createObjectURL(file);
```

Cette URL permet d’afficher le fichier avant son envoi. Elle ne doit pas être sauvegardée en base, car elle devient invalide après la fermeture de la page.

Lorsque l’aperçu n’est plus utile :

```js
URL.revokeObjectURL(previewUrl);
```

### 9.3 Envoi de l’image

```js
const formData = new FormData();
formData.append("scenarioId", String(scenario.value.id));
formData.append("title", entry.title || "");
formData.append("image", entry.file);
```

Le client envoie ensuite :

```http
POST /api/scenarios/{scenarioId}/thumbnails
Content-Type: multipart/form-data
```

Il ne faut pas définir manuellement la limite multipart dans le navigateur. Le navigateur ajoute lui même la bonne frontière au `Content-Type`.

### 9.4 Backend de l’image

`ThumbnailApiController` vérifie la requête et transmet le fichier à `ThumbnailService`.

`ThumbnailService.save` :

1. calcule le prochain index;
2. appelle `FileStorageService.storeThumbnail`;
3. remplit les métadonnées;
4. sauvegarde le modèle `Thumbnail`.

Le contenu est relu avec :

```http
GET /api/thumbnails/{id}/content
```

### 9.5 Mise à jour optimiste

La suppression d’une scène est optimiste. L’image est retirée immédiatement du tableau local pour que l’interface réagisse sans délai.

Si le serveur confirme la suppression, le nouvel état reste affiché.

Si le serveur refuse et que l’image existe toujours après un rechargement, le composable restaure la liste du serveur.

Un ensemble interne d’identifiants empêche un deuxième clic de lancer une requête concurrente pendant que la première suppression est en cours.

## 10. Déplacement, ordre et dimensions des images

### 10.1 Deux notions différentes

L’ordre du récit et la position dans la grille sont deux informations différentes.

`idx` indique l’ordre logique de la scène.

`gridColumn`, `gridRow`, `gridColumnSpan` et `gridRowSpan` indiquent son placement visuel.

### 10.2 Tri stable

`sortByIdxThenId` trie d’abord par `idx`, puis par identifiant. Le deuxième critère garde un ordre prévisible lorsque deux scènes possèdent accidentellement le même index.

### 10.3 Modes de mise en page

Le scénario conserve :

```text
storyboardLayoutMode
storyboardPreset
storyboardColumns
```

`PRESET` applique une disposition calculée.

`CUSTOM` utilise les dimensions stockées sur chaque image.

Les presets actuels comprennent `GRID_2`, `GRID_3`, `CINEMATIC` et `MANGA`.

### 10.4 Calcul d’un élément

`vite/src/utils/scenarioStoryboard.js` transforme une image en objet de rendu.

Exemple simplifié :

```js
{
  thumb,
  col: 1,
  row: 1,
  colSpan: 4,
  rowSpan: 3
}
```

`storyboardItemStyle` produit ensuite les propriétés CSS Grid :

```js
{
  gridColumn: `${col} / span ${colSpan}`,
  gridRow: `${row} / span ${rowSpan}`
}
```

### 10.5 Déplacement

`useThumbnailDragReorder.js` gère les événements du pointeur. Pendant le déplacement, `visualThumbnails` représente l’ordre temporaire affiché. À la fin, le composable appelle `applyOrder`.

`useThumbnailLifecycle.js` applique l’ordre localement puis tente de le sauvegarder. Si la sauvegarde échoue, il remet l’ordre précédent.

### 10.6 Redimensionnement

`useThumbnailResize.js` mesure la grille et le déplacement du pointeur. Il convertit le déplacement en nombre de colonnes et de rangées.

Les dimensions sont limitées afin d’éviter une valeur négative ou une scène qui dépasse complètement la grille.

La mise à jour persistée utilise :

```http
PATCH /api/thumbnails/{id}/layout
```

### 10.7 Modification du titre

Le titre local change pendant la saisie. La sauvegarde persistée utilise :

```http
PATCH /api/thumbnails/{id}/title
```

## 11. Vignette Maker

### 11.1 Vue d’ensemble

Le Vignette Maker se trouve dans `vite/src/components/VignetteMakerModal.vue`.

Il permet de choisir un arrière plan, d’ajouter des personnages, de modifier leur apparence, de changer leur pose, de les déplacer, de les redimensionner, de gérer leur profondeur et d’ajouter des bulles.

### 11.2 Rendu actuel des arrière plans

Dans la version actuelle du code, les arrière plans ne sont pas lus depuis des fichiers PNG. Ils sont dessinés par les fonctions Canvas de `vite/src/utils/vignetteBackgrounds.js`.

`SCENES` relie un identifiant, un nom et une fonction :

```js
export const SCENES = [
  {id: "hearth", label: "Fireplace", draw: drawHearth},
  {id: "market", label: "Village Market", draw: drawMarket},
  {id: "forest", label: "Sacred Grove", draw: drawForest}
];
```

`drawScene` retrouve la fonction puis dessine sur un canvas :

```js
export function drawScene(id, canvas, forStage) {
  const scene = SCENES.find((item) => item.id === id);
  if (scene) scene.draw(canvas, forStage);
}
```

Le canvas est converti en Data URL et placé dans une balise `<image>` du SVG de la scène.

Pour remplacer un arrière plan par un PNG à l’avenir, il faudra adapter `SCENES` et `renderBg`, puis attendre le chargement de l’image avant l’export final.

### 11.3 Personnages SVG

`vite/src/utils/vignetteMakerArt.js` contient les options et les fonctions de dessin.

```text
SKINS
HAIR_COLS
CLOTH_COLS
MOUTHS
EYES
ALL_HAIRS
CHARS
POSES
BUBBLE_THEMES
```

`personSVG` reçoit un objet personnage et retourne une chaîne SVG. `bubbleSVG` produit la bulle correspondante.

Les valeurs provenant de l’utilisateur sont échappées avec `escXML` avant d’être placées dans le SVG.

### 11.4 État d’une scène

L’état éditable ressemble à ceci :

```json
{
  "sceneId": "forest",
  "people": [
    {
      "uid": 1,
      "x": 245,
      "y": 175,
      "z": 1,
      "scale": 1,
      "age": "adult",
      "skin": "#D89A65",
      "hair": "curly",
      "hairColor": "#332017",
      "clothing": "#C65A7B",
      "pose": "wave"
    }
  ],
  "nextId": 2
}
```

Le JSON conserve les choix et les positions. Il ne contient pas le PNG final.

### 11.5 Déplacement et sélection

Chaque personnage est placé dans un groupe SVG. Un événement `mousedown` ou `touchstart` conserve le personnage déplacé et l’écart entre le pointeur et son origine.

Les déplacements suivants mettent à jour `x` et `y`. Le rendu est recalculé à partir de l’état.

La propriété `z` contrôle la profondeur. Les personnages sont triés avant le rendu :

```js
[...people].sort((a, b) => a.z - b.z)
```

### 11.6 Annuler et rétablir

`histStack` contient les états précédents.

`future` contient les états disponibles après une annulation.

Une nouvelle modification vide `future`. L’historique garde au maximum 60 états.

Une temporisation de 320 millisecondes évite de créer un état pour chaque pixel pendant un déplacement.

### 11.7 Sauvegarde locale

L’état courant est écrit sous la clé :

```text
vm-autosave
```

L’identifiant de la dernière scène sauvegardée utilise :

```text
vm-autosave-scene-id
```

Cette sauvegarde locale sert à reprendre une composition après la fermeture de la fenêtre. Elle n’est pas partagée entre les appareils.

### 11.8 Sauvegarde en base

Une scène nommée est envoyée sous cette forme :

```json
{
  "name": "Conversation près du feu",
  "sceneJson": "{\"sceneId\":\"hearth\",\"people\":[...]}"
}
```

Les routes sont :

```http
GET    /api/vignette-scenes
POST   /api/vignette-scenes
PATCH  /api/vignette-scenes/{id}
DELETE /api/vignette-scenes/{id}
```

`VignetteSceneService` utilise toujours l’identifiant de l’utilisateur. Une personne ne peut donc pas modifier une scène sauvegardée par une autre personne simplement en devinant son identifiant.

### 11.9 Export PNG

Le Vignette Maker conserve un état éditable, mais le storyboard attend une image.

Le pipeline est :

```text
Canvas de l’arrière plan
    ↓
SVG des personnages et bulles
    ↓
Rendu combiné
    ↓
Canvas d’export
    ↓
Data URL PNG
    ↓
Blob PNG
    ↓
File PNG
    ↓
FormData vers le backend
```

`insertIntoFrame` reçoit la Data URL, la transforme en Blob et émet l’événement `insert`.

`useSceneImageUpload.onVignetteMakerInsert` crée ensuite :

```js
const file = new File(
  [blob],
  `vignette-illustration-${Date.now()}.png`,
  {type: "image/png"}
);
```

Le PNG devient une image normale du storyboard. Modifier plus tard ce PNG ne modifie pas automatiquement la scène JSON sauvegardée. Ce sont deux représentations distinctes.

## 12. Enregistrements et prises audio

### 12.1 Organisation

Le Studio autorise plusieurs prises par image. Une prise audio persistée appartient à un `thumbnailId` et possède un `idx`.

`vite/src/composables/useVoiceTakes.js` assemble cinq composables :

```text
useVoiceSelection
useAudioFileImport
useQuickRecording
useDraftAudioImport
useRecordingLabels
```

Cette composition permet à `ScenarioDetailView.vue` d’utiliser une seule interface tout en gardant les responsabilités séparées.

### 12.2 Sélection d’une prise

`useVoiceSelection.js` choisit la prise active, le locuteur et le prochain emplacement disponible.

Une prise encore vide dans l’interface peut avoir `isDraft = true`. Elle devient une vraie prise après un enregistrement ou un import.

Les identifiants commençant par `local-` ne doivent pas être envoyés aux routes qui attendent un identifiant de base de données.

### 12.3 Interface du recorder

`vite/src/components/StudioRecorderPanel.vue` affiche :

1. la scène sélectionnée;
2. la prise active;
3. le locuteur;
4. le bouton du microphone;
5. l’import de fichier;
6. l’import depuis les brouillons;
7. les paramètres et le découpage.

Le composant reçoit l’état avec des props et remonte les actions avec des événements. La logique de capture ne se trouve pas directement dans le composant visuel.

### 12.4 Choix du format MediaRecorder

`useQuickRecording.getSupportedMimeType` teste plusieurs formats :

```js
const candidates = [
  "audio/webm;codecs=opus",
  "audio/webm",
  "audio/ogg;codecs=opus",
  "audio/ogg",
  "audio/mp4"
];
```

Le premier format reconnu par le navigateur est utilisé. Si aucun candidat n’est reconnu, `MediaRecorder` choisit son format par défaut.

### 12.5 Démarrage

`startQuickRecording` :

1. détermine la scène et la prise cible;
2. demande le microphone;
3. crée le MediaRecorder;
4. vide l’ancien tableau de morceaux;
5. commence l’enregistrement;
6. affiche un message de confirmation.

### 12.6 Arrêt et aperçu

Lors de l’arrêt, les morceaux deviennent un Blob. Une Object URL permet de prévisualiser ce Blob avant l’envoi.

L’utilisateur peut confirmer, recommencer ou abandonner.

Lors d’un abandon, les URL temporaires sont révoquées et une prise brouillon nouvellement créée est retirée.

### 12.7 Envoi d’une prise

```js
const formData = new FormData();
formData.append("title", title);
formData.append("idx", String(index));
formData.append("audio", audioFile, fileName);
```

Une nouvelle prise utilise :

```http
POST /api/thumbnails/{thumbnailId}/audios
```

Le remplacement d’une prise utilise :

```http
PUT /api/audios/{audioId}/content
```

Après l’envoi, le frontend recharge les prises de l’image afin d’utiliser les données du serveur plutôt qu’un objet local incomplet.

## 13. Importation et conversion des fichiers audio

### 13.1 Point d’entrée

`vite/src/composables/useAudioFileImport.js` reçoit un `File` provenant du sélecteur du navigateur.

### 13.2 Types envoyés directement

Le composable reconnaît notamment :

```text
MP3
WAV
OGG
WebM
MP4 ou M4A
AAC
FLAC
OPUS
AIFF
```

Si le type MIME est accepté par le backend, le fichier est envoyé sans conversion.

### 13.3 Conversion vers WAV

Si le type n’est pas reconnu, `decodeToWav` suit ces étapes :

```text
File
    ↓
ArrayBuffer
    ↓
AudioContext.decodeAudioData
    ↓
AudioBuffer
    ↓
PCM 16 bits entrelacé
    ↓
Blob audio/wav
    ↓
File .wav
```

Un WAV PCM commence par un en tête RIFF de 44 octets. Le code écrit les sections `RIFF`, `WAVE`, `fmt ` et `data`, puis ajoute les échantillons.

Chaque échantillon flottant est limité entre moins un et un, puis converti sur 16 bits signés.

### 13.4 Pourquoi le WAV peut être plus gros

Le PCM n’est pas compressé. Une minute stéréo à 48 kHz et 16 bits peut prendre environ 11 Mo. La conversion améliore la compatibilité, mais augmente parfois fortement la taille.

La limite multipart actuelle est de 25 Mo. Pour de longs audios, une conversion serveur avec un format compressé serait plus adaptée.

### 13.5 Erreurs de décodage

`AudioContext.decodeAudioData` dépend des codecs du navigateur. Deux fichiers avec la même extension peuvent se comporter différemment.

Le message d’erreur recommande les formats connus lorsque le décodage échoue.

## 14. Découpage audio

### 14.1 Séparation entre interface et traitement

`vite/src/composables/useAudioTrimEditor.js` gère les poignées, les pourcentages, l’aperçu et la sauvegarde.

`vite/src/utils/audioTrim.js` décode et réencode réellement l’audio.

### 14.2 Valeurs de découpage

Le début et la fin sont exprimés entre 0 et 100.

```js
const trimStart = ref(0);
const trimEnd = ref(100);
```

Le début doit toujours rester avant la fin. Une marge minimale d’un pour cent est imposée par l’interface.

### 14.3 Aperçu

L’aperçu charge l’audio courant, calcule les secondes correspondant aux pourcentages et commence à `startSeconds`.

Un écouteur `timeupdate` arrête le lecteur à `endSeconds`.

Cette prévisualisation ne modifie pas encore le fichier.

### 14.4 Découpage réel

```js
const {blob, durationSeconds} = await trimAudioSourceToWav(
  source,
  trimStart.value,
  trimEnd.value
);
```

`encodeAudioBufferRangeAsWav` calcule :

```js
const startFrame = Math.floor(audioBuffer.length * start / 100);
const endFrame = Math.ceil(audioBuffer.length * end / 100);
```

Seuls les échantillons entre ces deux positions sont copiés dans le nouveau WAV.

### 14.5 Sauvegarde locale ou persistée

Pour une prise locale, le Blob reçoit une nouvelle Object URL et remplace l’aperçu précédent.

Pour une prise persistée, le frontend crée un FormData puis appelle `replaceAudioContent`. Les prises de la scène sont ensuite rechargées.

Après une sauvegarde réussie, les valeurs reviennent à 0 et 100, car le nouveau fichier représente déjà la portion découpée.

## 15. Ambiances du scénario

### 15.1 Différence entre prise et ambiance

Une prise appartient à une image et possède :

```text
scope = SCENE
thumbnailId = identifiant de la scène
```

Une ambiance appartient au scénario entier :

```text
scope = BACKGROUND
thumbnailId = null
scenarioId = identifiant du scénario
```

### 15.2 Composable principal

`vite/src/composables/useBackgroundAmbience.js` gère la fenêtre, la liste, la sélection, l’import, les presets, la prévisualisation et le nettoyage des URL temporaires.

### 15.3 Aucun choix par défaut

Ouvrir la fenêtre ne doit pas ajouter automatiquement une ambiance. Le scénario peut rester sans ambiance.

Choisir un preset dans la bibliothèque prépare ce choix, mais l’utilisateur doit confirmer son ajout.

### 15.4 Presets synthétisés

`vite/src/utils/ambienceSynth.js` génère un court fichier WAV à partir de paramètres. Le Blob produit peut être écouté localement ou transformé en File puis envoyé comme une ambiance normale.

Le cache `presetAudioCache` évite de régénérer le même aperçu plusieurs fois. `disposePresetAudioCache` révoque toutes les Object URLs lors de la fermeture du Studio.

### 15.5 Import d’une ambiance

```js
const formData = new FormData();
formData.append("title", title);
formData.append("sourceLabel", sourceLabel);
formData.append("sourceUrl", sourceUrl);
formData.append("audio", uploadFile, uploadFile.name);
```

La route est :

```http
POST /api/scenarios/{scenarioId}/background-audios
```

Le nouvel audio devient l’ambiance active.

### 15.6 Sélection persistante

Un scénario peut avoir plusieurs ambiances importées, mais une seule ambiance active pour le lecteur.

```http
PATCH /api/scenarios/{scenarioId}/background-audio
Content-Type: application/json
```

```json
{
  "audioId": 91
}
```

`AudioService.selectBackgroundAudio` vérifie que l’audio possède le scope `BACKGROUND` et qu’il appartient au bon scénario.

Le modèle `Scenario` conserve :

```java
private Long activeBackgroundAudioId;
```

### 15.7 Suppression de l’ambiance active

Si l’ambiance active est supprimée, `AudioService.deleteAudio` cherche la prochaine ambiance du scénario. S’il n’en reste aucune, l’identifiant actif devient nul.

### 15.8 Volume et boucle

`backgroundVolume` et `backgroundLoop` contrôlent le lecteur de prévisualisation du Studio.

```js
player.volume = Number(backgroundVolume.value) / 100;
player.loop = Boolean(backgroundLoop.value);
```

Ces deux réglages ne sont pas encore stockés dans le modèle backend. Ils reviennent donc à leurs valeurs de départ après un nouveau chargement. Le lecteur public utilise actuellement sa propre boucle.

## 16. Lecteur et lecture automatique

### 16.1 Deux lecteurs complémentaires

`useScenarioAutoplay.js` joue les prises à l’intérieur du Studio et met en évidence la scène active.

`vite/src/components/scenario/ScenarioReaderModal.vue` fournit une expérience plein écran avec vue globale et vue par scène.

### 16.2 Construction de la file du Studio

`buildPlaybackQueue` parcourt les images triées, puis leurs audios triés.

Exemple :

```text
Scène 1, prise 1
Scène 1, prise 2
Scène 2, prise 1
Scène 3, prise 1
Scène 3, prise 2
```

La file est aplatie afin qu’un seul index puisse représenter la position courante.

### 16.3 useScenarioAutoplay

Le composable conserve :

```text
currentIndex
isPlaying
isPaused
currentItem
```

Il reçoit des callbacks pour mettre en évidence une image, arrêter l’ambiance et afficher la fin de la lecture.

### 16.4 Lecteur plein écran

`ScenarioReaderModal` recharge ses propres images et audios afin de pouvoir être ouvert depuis différentes pages.

Il trie toutes les prises d’une scène par `idx`, puis par identifiant.

Le bouton Play from start crée une file contenant toutes les prises de toutes les scènes.

Cliquer sur l’audio d’une scène dans la grille joue toutes les prises de cette scène dans l’ordre.

### 16.5 Fin d’une prise

`onAudioEnded` vérifie d’abord s’il existe une autre prise dans la scène. Si oui, il incrémente `sceneAudioIndex` et continue.

Lorsque la scène est terminée, il passe à la scène suivante si la lecture automatique est active.

### 16.6 Ambiance du lecteur

Le lecteur choisit d’abord la ligne marquée `active` :

```js
backgroundAudios.value.find((audio) => audio.active)
```

Pour rester compatible avec les anciennes données, il utilise la première ambiance si aucune ligne n’est marquée.

Une balise audio séparée tourne en boucle. Elle peut être coupée sans interrompre la voix.

### 16.7 Nettoyage

À la fermeture, le lecteur doit :

1. arrêter la voix;
2. arrêter l’ambiance;
3. annuler les temporisations;
4. retirer les écouteurs clavier;
5. quitter le plein écran si nécessaire.

## 17. Publication et modifications après publication

### 17.1 États

Le modèle `Scenario` utilise notamment :

```text
DRAFT
PUBLISHED
```

La première publication remplit `publishedAt` et peut déclencher les notifications prévues pour une nouvelle publication.

### 17.2 Modifications après publication

Lorsque le scénario est déjà publié, les wrappers de `ScenarioDetailView.vue` appellent `markEditedIfPublished` après une modification réussie.

Le rappel utilise une clé locale :

```text
vignette:scenario:{id}:publication-edit
```

Le rappel survit à un rechargement de la page dans le même navigateur.

### 17.3 Confirmation de mise à jour

La confirmation appelle de nouveau :

```http
POST /api/scenarios/{id}/publish
```

`ScenarioService.publishScenario` actualise `publishedAt` et crée une entrée d’historique adaptée.

Les abonnés à une langue sont notifiés seulement lors de la première publication, pas à chaque confirmation de mise à jour.

### 17.4 Limite de cette approche

Le projet ne conserve pas encore deux copies séparées, une publique et une en cours de modification.

Les changements sont enregistrés immédiatement sur le scénario. Le rappel indique que la version doit être reconfirmée, mais il ne cache pas les modifications derrière un véritable brouillon serveur.

Pour obtenir un vrai système de versions, il faudrait ajouter un modèle de révision ou un instantané publié contenant les métadonnées, l’ordre des images, les médias sélectionnés et la mise en page.

## 18. Backend, API et modèles de données

### 18.1 Client API frontend

`vite/src/api/scenarios.js` regroupe les appels du volet scénario. Les composants et composables ne devraient pas construire chacun leur propre `fetch` lorsque la route appartient à ce domaine.

Exemple :

```js
export function fetchScenario(id) {
  return apiFetch(`/api/scenarios/${id}`);
}

export function publishScenario(id) {
  return apiFetch(`/api/scenarios/${id}/publish`, {
    method: "POST"
  });
}
```

### 18.2 apiFetch

`vite/src/api/rest.js` fournit le comportement commun.

Il ajoute `Accept: application/json`.

Il transforme automatiquement un objet JavaScript en JSON.

Il laisse FormData intact.

Il ajoute les cookies avec `credentials: "include"`.

Il ajoute le jeton Bearer lorsque nécessaire.

Il ajoute le jeton CSRF aux requêtes qui modifient l’état.

Il tente un renouvellement après une réponse 401.

Il transforme les réponses d’erreur en exceptions JavaScript.

### 18.3 Routes de scénario

```http
POST   /api/scenarios
GET    /api/scenarios/{id}
GET    /api/scenarios/mine
GET    /api/scenarios/shared-with-me
PATCH  /api/scenarios/{id}/metadata
PATCH  /api/scenarios/{id}/storyboard
POST   /api/scenarios/{id}/publish
DELETE /api/scenarios/{id}
```

### 18.4 Routes d’image

```http
GET    /api/scenarios/{scenarioId}/thumbnails
POST   /api/scenarios/{scenarioId}/thumbnails
GET    /api/thumbnails/{id}/content
PATCH  /api/thumbnails/{id}/layout
PATCH  /api/thumbnails/{id}/title
DELETE /api/thumbnails/{id}
```

Le frontend contient également la préparation de l’appel suivant :

```http
PATCH /api/scenarios/{scenarioId}/thumbnails/reorder
```

Le réordonnancement visuel a été commencé dans le frontend, mais la partie backend nécessaire pour conserver définitivement le nouvel ordre n’a pas pu être terminée dans le temps prévu pour le projet. L’appel API est déjà préparé afin de faciliter la continuation. Voir la section 23.2 pour les étapes restantes.

### 18.5 Routes audio

```http
GET    /api/thumbnails/{thumbnailId}/audios
POST   /api/thumbnails/{thumbnailId}/audios
GET    /api/audios/{id}/content
PUT    /api/audios/{id}/content
PATCH  /api/audios/{id}/marker
DELETE /api/audios/{id}
```

Le Studio prépare aussi l’appel suivant pour la transcription, la glose et la traduction libre :

```http
PATCH /api/audios/{audioId}/gloss
```

Les champs peuvent rester dans l’état local d’une prise qui n’a pas encore été envoyée. La route backend, les champs du modèle `Audio` et leur migration n’ont toutefois pas été finalisés dans le temps prévu pour le projet. Une prise déjà persistée ne peut donc pas encore conserver ces annotations après un rechargement. Voir la section 23.3.

### 18.6 Routes d’ambiance

```http
GET    /api/scenarios/{scenarioId}/background-audios
POST   /api/scenarios/{scenarioId}/background-audios
PATCH  /api/scenarios/{scenarioId}/background-audio
```

### 18.7 Routes du Vignette Maker

```http
GET    /api/vignette-scenes
POST   /api/vignette-scenes
PATCH  /api/vignette-scenes/{id}
DELETE /api/vignette-scenes/{id}
```

### 18.8 Modèle Scenario

Champs importants du volet création :

```text
id
title
description
authorId
languageId
visibilityStatus
publishedAt
activeBackgroundAudioId
storyboardLayoutMode
storyboardPreset
storyboardColumns
```

### 18.9 Modèle Thumbnail

```text
id
title
idx
scenarioId
storagePath
contentType
sizeBytes
originalFilename
imageSha256
gridColumn
gridRow
gridColumnSpan
gridRowSpan
imageWidth
imageHeight
```

### 18.10 Modèle Audio

```text
id
title
idx
mime
scope
scenarioId
thumbnailId
storagePath
sizeBytes
audioSha256
sourceLabel
sourceUrl
markerX
markerY
markerLabel
```

### 18.11 Modèle VignetteScene

```text
id
userId
name
sceneJson
createdAt
updatedAt
```

## 19. Stockage des fichiers et sécurité

### 19.1 FileStorageService

Le service se trouve dans :

```text
src/main/java/org/titiplex/service/storage/FileStorageService.java
```

Le dossier par défaut est :

```text
./data/storage
```

Il peut être remplacé avec :

```text
APP_STORAGE_ROOT
```

### 19.2 Organisation logique

```text
thumbnails/scenario-{scenarioId}/...
audios/scenario-{scenarioId}/thumbnail-{thumbnailId}/...
audios/scenario-{scenarioId}/background/...
```

### 19.3 SHA 256

Le service calcule le SHA 256 pendant la copie du fichier. Le chemin final contient le hash et son extension.

Cette structure :

1. donne un nom stable;
2. limite les doublons physiques;
3. permet de fournir un ETag;
4. répartit les fichiers dans plusieurs sous dossiers.

### 19.4 Écriture sûre

Le fichier est d’abord écrit dans un fichier temporaire. Il est ensuite déplacé vers son chemin final, idéalement avec un déplacement atomique.

Le chemin final est normalisé et doit rester sous la racine de stockage. Cela empêche un chemin comme `../../autre-dossier` de sortir de l’espace autorisé.

### 19.5 Suppression après transaction

Lorsqu’une ligne doit être remplacée, le fichier précédent est supprimé après la réussite de la transaction. Cette séquence évite de perdre l’ancien fichier si la base refuse la mise à jour.

Le service vérifie aussi si un fichier est encore référencé avant de le supprimer lorsqu’un même contenu est partagé.

### 19.6 Permissions

Les contrôleurs utilisent les annotations de sécurité du projet et les services vérifient la visibilité ou l’accès en modification.

Les classes suivantes sont importantes :

```text
ScenarioSecurity.java
ThumbnailSecurity.java
AudioSecurity.java
```

Le frontend ne remplace jamais la sécurité du backend. Masquer un bouton améliore l’interface, mais le contrôleur et le service doivent toujours refuser une requête non autorisée.

## 20. Carte des fichiers

### 20.1 Navigation et structure globale

```text
vite/src/App.vue
vite/src/router/index.js
vite/src/layouts/AppShell.vue
```

### 20.2 Espace personnel et création

```text
vite/src/views/WorkspaceHomeView.vue
vite/src/views/CreateScenarioView.vue
vite/src/api/scenarios.js
```

### 20.3 Enregistrement d’urgence

```text
vite/src/components/EmergencyAudioRecorder.vue
vite/src/utils/draftAudioStorage.js
vite/src/composables/useDraftAudioImport.js
```

### 20.4 Studio

```text
vite/src/views/ScenarioDetailView.vue
vite/src/components/StudioRecorderPanel.vue
vite/src/components/ThumbnailCard.vue
```

### 20.5 Images et storyboard

```text
vite/src/composables/useSceneImageUpload.js
vite/src/composables/useThumbnailLifecycle.js
vite/src/composables/useThumbnailResize.js
vite/src/composables/useThumbnailDragReorder.js
vite/src/utils/scenarioStoryboard.js
```

### 20.6 Vignette Maker

```text
vite/src/components/VignetteMakerModal.vue
vite/src/utils/vignetteMakerArt.js
vite/src/utils/vignetteBackgrounds.js
vite/src/api/vignetteScenes.js
```

### 20.7 Audio

```text
vite/src/composables/useVoiceTakes.js
vite/src/composables/useVoiceSelection.js
vite/src/composables/useAudioGloss.js
vite/src/composables/useQuickRecording.js
vite/src/composables/useAudioFileImport.js
vite/src/composables/useDraftAudioImport.js
vite/src/composables/useAudioTrimEditor.js
vite/src/utils/audioTrim.js
```

### 20.8 Ambiance et lecteur

```text
vite/src/composables/useBackgroundAmbience.js
vite/src/utils/ambienceSynth.js
vite/src/composables/useScenarioAutoplay.js
vite/src/composables/useScenarioReader.js
vite/src/components/scenario/ScenarioReaderModal.vue
```

### 20.9 Backend

```text
src/main/java/org/titiplex/api/ScenarioApiController.java
src/main/java/org/titiplex/api/ThumbnailApiController.java
src/main/java/org/titiplex/api/AudioApiController.java
src/main/java/org/titiplex/api/VignetteSceneApiController.java

src/main/java/org/titiplex/service/ScenarioService.java
src/main/java/org/titiplex/service/ThumbnailService.java
src/main/java/org/titiplex/service/AudioService.java
src/main/java/org/titiplex/service/VignetteSceneService.java

src/main/java/org/titiplex/persistence/model/Scenario.java
src/main/java/org/titiplex/persistence/model/Thumbnail.java
src/main/java/org/titiplex/persistence/model/Audio.java
src/main/java/org/titiplex/persistence/model/VignetteScene.java
```

### 20.10 Si je veux modifier une fonctionnalité

Pour le bouton d’urgence, commencer par `EmergencyAudioRecorder.vue`.

Pour la liste des brouillons, commencer par `WorkspaceHomeView.vue` et `draftAudioStorage.js`.

Pour le formulaire de création, commencer par `CreateScenarioView.vue`.

Pour la mise en page générale du Studio, commencer par `ScenarioDetailView.vue`.

Pour les libellés et boutons du recorder, commencer par `StudioRecorderPanel.vue` et `useRecordingLabels.js`.

Pour la capture micro, commencer par `useQuickRecording.js`.

Pour l’import d’un fichier audio, commencer par `useAudioFileImport.js`.

Pour le découpage, commencer par `useAudioTrimEditor.js` et `audioTrim.js`.

Pour ajouter une option de personnage, commencer par `vignetteMakerArt.js`.

Pour ajouter un arrière plan, commencer par `vignetteBackgrounds.js` et la liste `SCENES`.

Pour modifier l’export du Maker, chercher `exportPNG` et `insertIntoFrame` dans `VignetteMakerModal.vue`.

Pour modifier l’import d’image, commencer par `useSceneImageUpload.js`.

Pour modifier l’ordre, commencer par `useThumbnailDragReorder.js` et `useThumbnailLifecycle.js`.

Pour modifier les dimensions, commencer par `useThumbnailResize.js` et `scenarioStoryboard.js`.

Pour modifier les ambiances, commencer par `useBackgroundAmbience.js`.

Pour modifier la lecture plein écran, commencer par `ScenarioReaderModal.vue`.

Pour modifier la publication, lire `publishCurrentScenario`, `confirmUpdatePublished` et `ScenarioService.publishScenario`.

## 21. Tests

### 21.1 Frontend unitaire

```bash
cd vite
npm run test:run
```

Tests importants du volet création :

```text
tests/unit/composables/useAudioTrimEditor.test.js
tests/unit/composables/useBackgroundAmbience.test.js
tests/unit/composables/useThumbnailLifecycle.test.js
tests/unit/composables/useThumbnailResize.test.js
tests/unit/composables/useThumbnailDragReorder.test.js
tests/unit/utils/audioTrim.test.js
tests/unit/utils/draftAudioStorage.test.js
tests/unit/utils/scenarioStoryboard.test.js
tests/unit/utils/vignetteMakerArt.test.js
tests/unit/views/ScenarioDetailView.test.js
```

### 21.2 Ce que les tests vérifient

Le découpage vérifie les limites, l’encodage WAV, la sauvegarde locale et le remplacement serveur.

Les ambiances vérifient l’absence de sélection automatique, l’import, la sélection persistante, la lecture et le nettoyage des URL.

Le cycle de vie des images vérifie la sélection, le titre, la suppression optimiste, la restauration et l’ordre.

Le storyboard vérifie le tri, les tailles automatiques, les presets et le mode personnalisé.

La vue du Studio vérifie le chargement, la publication, l’import d’image, les paramètres du storyboard et la sélection d’une prise.

### 21.3 Playwright

```bash
cd vite
npx playwright test
```

`studio-flow.spec.js` simule un parcours complet :

```text
ouvrir le Studio
créer une image avec le Maker
importer un audio
ajouter une ambiance
ouvrir le lecteur
publier
```

`scenario-playback.spec.js` vérifie la sélection des scènes, plusieurs prises et l’ambiance active.

Les appels API sont simulés dans ces tests. Un test Playwright vert confirme le parcours frontend, mais ne remplace pas un test d’intégration avec le vrai backend.

### 21.4 Backend

```bash
mvn test
```

Tests importants :

```text
AudioServiceTest.java
AudioApiControllerTest.java
AudioApiControllerWebMvcTest.java
ScenarioServiceTest.java
ThumbnailServiceTest.java
VignetteSceneServiceTest.java
```

Les tests MockMvc vérifient aussi l’authentification et le jeton CSRF.

### 21.5 Build de production

```bash
cd vite
npm run build
```

Le build doit être exécuté après une modification des imports, des composants ou du routeur. Un test unitaire peut passer même si un import utilisé uniquement dans le template est incorrect.

### 21.6 Test manuel conseillé

Après une modification importante du Studio :

1. créer un scénario;
2. ajouter une image importée;
3. créer une image avec le Maker;
4. déplacer et redimensionner les deux scènes;
5. enregistrer deux prises sur une scène;
6. importer un brouillon sur l’autre scène;
7. découper une prise;
8. ajouter deux ambiances et sélectionner la deuxième;
9. ouvrir le lecteur;
10. vérifier l’ordre de toutes les prises;
11. couper et réactiver l’ambiance;
12. publier;
13. modifier une image publiée;
14. recharger la page et vérifier le rappel de publication.

## 22. Dépannage

### 22.1 Le microphone ne démarre pas

Vérifier que la page est ouverte sur `localhost` ou en HTTPS. Les navigateurs bloquent généralement le microphone sur une origine HTTP distante.

Vérifier les permissions du site dans le navigateur.

Vérifier si une autre application utilise exclusivement le microphone.

Consulter `recordingErrorMessage` dans `EmergencyAudioRecorder.vue` et la gestion d’erreur dans `useQuickRecording.js`.

### 22.2 Le brouillon n’apparaît pas

Ouvrir les outils de développement, puis Application et Local Storage.

Vérifier la clé anonyme et la clé portant le nom de l’utilisateur.

Vérifier que l’objet contient `id` et `dataUrl`.

Déclencher une nouvelle sauvegarde et vérifier l’événement `vignette:draft-audios-changed`.

### 22.3 L’URL change mais la page ne se recharge pas

Vérifier que `App.vue` utilise toujours `route.fullPath` comme clé du composant.

Vérifier que le code utilise `router.push` ou `router.replace` au lieu de modifier manuellement l’historique.

Vérifier les watchers sur `props.id` si le composant n’est plus recréé.

### 22.4 Le Studio demande une image après un brouillon

C’est le comportement attendu. Un audio de scène doit avoir un `thumbnailId`. Ajouter ou créer la première image permet ensuite à `applyUnclaimedDraftAudio` de terminer l’import.

### 22.5 Une image ne disparaît qu’après rechargement

Vérifier `deleteThumb` dans `useThumbnailLifecycle.js`.

La scène doit être retirée du tableau local avant l’appel serveur. Vérifier aussi que `loadThumbs` ne réinsère pas une ancienne réponse arrivée en retard.

### 22.6 Une deuxième suppression produit une erreur de base

Vérifier l’ensemble qui protège les suppressions en cours. Le bouton doit être désactivé ou le second appel ignoré jusqu’à la fin du premier.

### 22.7 Une image est refusée par le backend

Vérifier le type MIME réel, pas seulement l’extension.

Vérifier `FileStorageService.EXTENSIONS`.

Vérifier la limite multipart de 25 Mo.

Pour une grande image, exporter en WebP ou JPEG peut réduire la taille.

### 22.8 Un audio ne peut pas être lu ou décodé

Tester un WAV ou un MP3 simple.

Vérifier `file.type` dans la console du navigateur.

Vérifier si `prepareAudioUploadFile` envoie le fichier directement ou lance `decodeToWav`.

Un conteneur peut utiliser un codec absent du navigateur même si son extension est reconnue.

### 22.9 Le découpage semble ne rien faire

Vérifier que les poignées ne sont pas encore à 0 et 100.

Vérifier que la source audio retourne HTTP 200.

Vérifier que `replaceAudioContent` termine avant le rechargement des prises.

Après la sauvegarde, les valeurs reviennent volontairement à 0 et 100.

### 22.10 L’ambiance existe mais le lecteur en joue une autre

Vérifier la réponse de :

```http
GET /api/scenarios/{scenarioId}/background-audios
```

Une seule ligne devrait avoir `active: true`.

Vérifier `scenario.activeBackgroundAudioId` dans la base.

Vérifier que le PATCH de sélection a été envoyé après le clic.

### 22.11 Le lecteur ne joue qu’une prise

Vérifier que toutes les prises sont présentes dans `audioMap`.

Vérifier leur `idx`.

Vérifier `sceneAudioIndex`, `currentAudios` et `onAudioEnded` dans `ScenarioReaderModal.vue`.

### 22.12 Les changements locaux sont perdus

Vérifier si le Studio était en mode temporaire. Les identifiants `emergency-` et `local-` indiquent que les données n’étaient pas encore persistées.

Vérifier aussi si une Object URL a été sauvegardée à la place du vrai fichier.

### 22.13 La base fonctionne en dev mais pas en production

Le profil dev permet à Hibernate de mettre le schéma à jour. La production utilise `ddl-auto=validate` et Flyway.

Toute nouvelle colonne nécessaire en production doit donc avoir une migration.

### 22.14 Une glose disparaît après le rechargement

Vérifier d’abord si la prise possède un identifiant local ou un identifiant de base de données.

Pour une prise locale, `useAudioGloss.js` garde les trois valeurs dans l’état du Studio. Pour une prise persistée, le frontend tente d’appeler :

```http
PATCH /api/audios/{audioId}/gloss
```

Cette route n’est pas encore disponible côté backend. Il faut terminer la persistance décrite à la section 23.3 avant de considérer le rechargement des annotations comme pris en charge.

## 23. Limites actuelles et pistes de continuation

### 23.1 Taille de ScenarioDetailView

La vue centrale reste volumineuse. Une prochaine étape serait de déplacer davantage de sections visuelles vers des composants spécialisés et de conserver seulement l’orchestration dans la vue.

Une extraction doit être accompagnée de tests afin d’éviter de casser les nombreuses dépendances entre la sélection, l’enregistrement et le storyboard.

### 23.2 Persistance de l’ordre

Le déplacement visuel des scènes et l’appel frontend vers `PATCH /api/scenarios/{id}/thumbnails/reorder` ont été préparés. La persistance complète du nouvel ordre côté backend faisait partie des dernières améliorations prévues, mais elle n’a pas pu être finalisée avant la fin du projet.

Pour terminer cette fonctionnalité, la prochaine équipe peut ajouter un DTO contenant `thumbnailIds`, créer la route correspondante dans `ThumbnailApiController`, vérifier que toutes les images appartiennent au scénario, puis mettre à jour leurs valeurs `idx` dans une transaction. Les composables frontend et leurs mécanismes de retour arrière sont déjà en place.

### 23.3 Persistance des annotations linguistiques

Le Studio contient déjà les champs de transcription, de glose et de traduction libre ainsi que la logique frontend de sauvegarde. Cette partie a été préparée pour permettre l’annotation d’une prise, mais sa persistance backend n’a pas pu être finalisée avant la fin du projet.

Pour terminer le parcours, il faut ajouter les champs correspondants au modèle `Audio`, créer une migration Flyway, compléter `AudioRowDto`, ajouter un DTO de mise à jour et implémenter `PATCH /api/audios/{audioId}/gloss` avec la même vérification de propriétaire que les autres modifications audio. Il faudra ensuite tester qu’une annotation demeure visible après un rechargement complet.

### 23.4 Options de diffusion à la création

La page de création propose les choix d’audience et les options concernant les commentaires, la transcription et le téléchargement. Leur interface a été réalisée, mais le modèle backend ne contient pas encore les champs nécessaires pour les conserver.

Le fonctionnement persistant actuel repose sur `visibilityStatus`. Un scénario est créé comme brouillon, puis devient public lors de sa publication. Pour rendre les options du formulaire effectives, la prochaine équipe devra définir précisément leur comportement, ajouter les champs au modèle et aux DTO, créer une migration et appliquer ces règles dans les contrôleurs de lecture et de téléchargement.

### 23.5 Brouillons audio dans localStorage

Le stockage local est simple, mais limité. Une évolution possible serait IndexedDB, qui accepte mieux les Blob et offre davantage d’espace.

Une autre option serait un endpoint de brouillons privés. Cette solution permettrait la synchronisation entre appareils, mais demanderait une politique de suppression et de confidentialité.

### 23.6 Conversion audio dans le navigateur

Le WAV est compatible mais volumineux. Une conversion serveur avec FFmpeg permettrait de produire un format compressé cohérent, de traiter les fichiers non décodables par le navigateur et d’éviter un travail lourd sur les appareils mobiles.

### 23.7 Volume et boucle des ambiances

Ces préférences ne sont pas persistées. Il serait possible d’ajouter `backgroundVolume` et `backgroundLoop` au scénario ou de créer une relation de configuration entre le scénario et l’audio actif.

Le DTO et le lecteur devraient ensuite utiliser les valeurs du serveur.

### 23.8 Véritable version publiée

Le rappel de publication est local. Une architecture plus complète créerait un instantané ou une révision serveur.

Il faudrait déterminer quelles données sont versionnées :

1. métadonnées;
2. ordre des scènes;
3. dimensions;
4. images;
5. prises audio;
6. ambiance active;
7. informations linguistiques.

### 23.9 Arrière plans du Vignette Maker

Les fonctions Canvas sont autonomes, mais un catalogue de PNG ou WebP simplifierait le travail des illustrateurs.

Une migration vers des fichiers devrait conserver un identifiant stable par scène afin que les anciens JSON restent chargeables.

### 23.10 Accessibilité

Les commandes clavier du lecteur sont déjà présentes. Les prochaines améliorations peuvent couvrir la navigation complète au clavier dans le Maker, les descriptions d’images, une indication visible du focus et des annonces adaptées pendant l’enregistrement.

### 23.11 Performance

Le bundle frontend principal est volumineux. Le chargement différé de certaines pages et du Vignette Maker réduirait le JavaScript chargé au premier affichage.

Les grandes images devraient aussi être redimensionnées ou compressées avant l’envoi.

## 24. Glossaire

### API

Interface utilisée par le frontend pour communiquer avec le backend par HTTP.

### ArrayBuffer

Zone mémoire contenant des octets. Utilisée pour lire et décoder les fichiers.

### Blob

Objet représentant des données binaires en mémoire, comme un audio ou une image.

### Canvas

Surface de dessin en pixels contrôlée avec JavaScript.

### Composable

Fonction Vue regroupant un état réactif et des actions réutilisables.

### CSRF

Protection contre les requêtes envoyées à l’insu d’un utilisateur connecté.

### Data URL

Chaîne contenant le type et le contenu Base64 d’un fichier.

### DTO

Objet conçu pour transférer des données entre le backend et le frontend.

### ETag

Identifiant HTTP du contenu d’un fichier, utilisé pour le cache.

### File

Blob possédant un nom, une date et un type MIME.

### FormData

Format de requête utilisé pour envoyer des champs et des fichiers.

### JPA

Technologie Java qui relie les objets aux tables de base de données.

### localStorage

Petit stockage clé valeur conservé par le navigateur.

### MediaRecorder

API du navigateur qui enregistre un MediaStream.

### MediaStream

Flux provenant notamment d’un microphone.

### MIME

Nom standard du type de contenu, par exemple `audio/webm` ou `image/png`.

### Object URL

URL temporaire commençant par `blob:` et pointant vers un Blob en mémoire.

### PCM

Représentation non compressée des échantillons audio.

### PNG

Format d’image matricielle utilisé pour le rendu final du Vignette Maker.

### Repository

Interface JPA utilisée pour lire et écrire les modèles.

### SVG

Format vectoriel utilisé pour les personnages et les bulles du Vignette Maker.

### WAV

Conteneur audio utilisé ici avec des échantillons PCM 16 bits.

## 25. Liste de vérification avant une contribution

### 25.1 Avant de modifier

1. identifier le parcours utilisateur;
2. trouver le composant visuel;
3. trouver le composable responsable;
4. trouver l’appel dans `api`;
5. trouver le contrôleur et le service;
6. vérifier si une migration est nécessaire;
7. trouver les tests existants.

### 25.2 Pendant la modification

1. garder les identifiants locaux séparés des identifiants de base;
2. révoquer les Object URLs inutilisées;
3. arrêter les MediaStreams et les lecteurs;
4. gérer les erreurs du navigateur;
5. ne pas faire confiance uniquement aux contrôles du frontend;
6. mettre à jour l’état local après une réponse serveur;
7. prévoir un retour arrière pour les mises à jour optimistes;
8. marquer les modifications d’un scénario publié.

### 25.3 Avant de proposer le changement

```bash
git diff --check
```

```bash
cd vite
npm run test:run
npm run build
npx playwright test
```

```bash
mvn test
```

Enfin, tester manuellement le parcours modifié avec le vrai backend. Vérifier le rechargement de la page, car un état qui fonctionne seulement en mémoire peut masquer une persistance incomplète.

## Conclusion

Le volet création relie plusieurs types de données qui n’ont pas le même cycle de vie. Les métadonnées sont en JSON, les médias persistés sont stockés comme fichiers, les brouillons d’urgence restent d’abord dans le navigateur, les scènes du Vignette Maker possèdent un état JSON éditable et un rendu PNG séparé, puis le lecteur réunit les images, les prises et l’ambiance active.

Pour maintenir ce volet sans casser un autre parcours, il faut toujours suivre la donnée depuis l’action de l’utilisateur jusqu’à son stockage, puis refaire le trajet inverse au rechargement. La carte des fichiers et les tests décrits dans ce guide fournissent un point de départ pour chaque partie.
