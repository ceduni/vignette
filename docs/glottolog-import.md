# Import Glottolog — Documentation du processus

> **V1 (2026)** : le pipeline d’import est désormais un **worker Python externe**
> qui écrit directement dans PostgreSQL. Java ne lance plus Python, n’importe plus
> le CSV et ne planifie plus l’auto-update. Java gère la config admin (auto on/off,
> fréquence en jours) et dépose les demandes manuelles en base. L’état opérationnel
> (`next_run_at`, checksums, statuts, retries, verrous) appartient à Python
> (`glottolog_pipeline_state`). Voir `scripts/glottolog_worker/`.
>
> L’ancien chemin Java (`ProcessBuilder`, `@Scheduled`, import CSV) reste dans le
> code derrière `app.glottolog.legacy-pipeline-enabled=false` (désactivé par défaut)
> jusqu’à validation E2E complète.
>
> ### Lancer le worker (PostgreSQL requis)
>
> ```bash
> pip install -r scripts/glottolog_worker/requirements.txt
> cp scripts/glottolog_worker/.env.example scripts/glottolog_worker/.env
> # Éditer .env : GLOTTOLOG_SMTP_USER / FROM = ton Gmail
> #               GLOTTOLOG_SMTP_PASSWORD = App Password Google (16 chars)
> # Ne jamais committer le fichier .env
> export DATABASE_URL='postgresql://user:pass@localhost:5432/vignette'
> # ou PGHOST / PGPORT / PGDATABASE / PGUSER / PGPASSWORD
> cd scripts && python -m glottolog_worker          # boucle événementielle
> cd scripts && python -m glottolog_worker --once   # un seul cycle
> ```
>
> Test email : `cd scripts && python -m glottolog_worker.send_test_email --to ton.adresse@gmail.com`  
> Dans l’admin → Schedule, renseigner aussi **Notification email** (destinataire).
>
> Le worker **ne poll pas** toutes les 30s pour savoir si 14 jours sont passés.
> Il lit `next_run_at`, dort jusqu’à cette date, et se réveille sur :
> - `NOTIFY glottolog_wake` (demande manuelle ou changement de config),
> - `next_run_at` / `next_retry_at` atteint,
> - contrôle de secours (`GLOTTOLOG_SAFETY_CHECK_SECONDS`, défaut **6 h**),
> - redémarrage (si `next_run_at` est déjà dépassé → exécution immédiate).
>
> Admin → **Update** dépose une demande `PENDING` ; un trigger PG notifie le worker.
> **Schedule** (auto + fréquence jours) est relu par Python qui recalcule `next_run_at`.

Ce document explique **en détail** comment les données de langues de Glottolog
sont récupérées, filtrées, normalisées puis synchronisées dans la base de
l'application. Il détaille surtout **pourquoi la comparaison avec la source
externe est aussi rapide**, ce que sont les notions de **checksum** et de
**fingerprint local**, et **tous les cas** qui peuvent se produire lors d'un
import.

---

## 1. Vue d'ensemble

L'import repose sur une idée simple : **ne jamais refaire un travail déjà fait.**

Un cycle complet télécharge ~200 Mo d'archive CLDF depuis Zenodo, la décompresse,
lit plusieurs CSV, reconstruit un `languoid.csv` filtré, puis écrit en base. C'est
coûteux. On veut donc pouvoir répondre « rien à faire » en **quelques
millisecondes** quand rien n'a changé.

Pour cela, le système compare la source à **trois niveaux**, du moins cher au
plus cher :

| Niveau | Ce qu'on compare | Coût | Évite… |
|--------|------------------|------|--------|
| 1. **Checksum Zenodo** | Empreinte du zip distant vs `.zenodo.fingerprint` local | 1 requête API (~quelques Ko) | Le téléchargement + décompression |
| 2. **Hash du CSV** | SHA-256 du `languoid.csv` vs `.imported.sha256` | Lecture d'un fichier local | L'écriture en base |
| 3. **Diff ligne à ligne** | Chaque langue CSV vs la ligne en base | Parcours en mémoire | Les écritures inutiles (rows identiques) |

> L'idée clé : **on compare des empreintes, pas les contenus complets.** Comparer
> deux chaînes de 64 caractères est instantané ; télécharger et relire 200 Mo ne
> l'est pas.

### Schéma du flux

```
Admin / Scheduler
      │  startJob()
      ▼
GlottologUpdateJobService  ── exécute en tâche de fond (1 thread), publie la progression
      │  downloadAndSync()
      ▼
GlottologUpdateService
      │  1) fetchRemoteFingerprint()  ── API Zenodo  ─────────► compare au .zenodo.fingerprint
      │  2) runDownloadScript()       ── (seulement si nécessaire)
      │        └── scripts/download_glottolog_languoid.py
      │  3) sha256(languoid.csv)      ─────────────────────────► compare au .imported.sha256
      │  4) syncFromCsv()
      ▼
LanguageImportService  ── lit le CSV, filtre, normalise, écrit inserts/updates/suppressions
      ▼
Base de données (H2 en dev, PostgreSQL en prod)
```

---

## 2. Les acteurs

| Composant | Rôle |
|-----------|------|
| `scripts/download_glottolog_languoid.py` | Télécharge le zip CLDF depuis Zenodo, le décompresse, filtre et écrit `languoid.csv`. Fait aussi sa **propre** vérification checksum. |
| `GlottologUpdateService.java` | Orchestre le tout : check Zenodo, appel du script, hash du CSV, décision « skip ou pas », appel de la sync. |
| `LanguageImportService.java` | Lit `languoid.csv`, applique la sélection/normalisation, calcule les inserts/updates/suppressions et écrit en base. |
| `GlottologUpdateJobService.java` | Rend l'import **asynchrone** : lance le job sur un thread dédié et expose l'avancement (barre de progression, logs). |
| `GlottologScheduledUpdateService.java` | Déclenche automatiquement un import quand la fréquence configurée est atteinte. |
| `GlottologAdminService.java` | Réglages admin (auto-update on/off, fréquence) + historique des imports. |

---

## 3. Checksum et fingerprint : les deux notions clés

### 3.1 Qu'est-ce qu'un checksum ?

Un **checksum** (ou empreinte / hash) est une **courte signature calculée à
partir du contenu d'un fichier**. Sa propriété essentielle :

- Le **même contenu** produit **toujours** le même checksum.
- **La moindre modification** (un octet) change complètement le checksum.

Ça permet de répondre à la question *« ce fichier a-t-il changé ? »* **sans
comparer les fichiers octet par octet** : on compare juste les deux signatures.

Deux algorithmes sont utilisés dans ce projet :

- **MD5** : c'est ce que **Zenodo** publie pour le zip CLDF. On ne le calcule
  pas nous-mêmes, on le lit dans la réponse de l'API. Format :
  `md5:a90ec97b56abb432dc02ca7b5a838272`.
- **SHA-256** : c'est ce que **nous** calculons localement sur le
  `languoid.csv` généré, pour détecter si le CSV a changé depuis le dernier
  import réussi. Format : chaîne hexadécimale de 64 caractères.

> Pourquoi deux algos ? MD5 vient de Zenodo (on subit leur choix). SHA-256 est
> notre choix côté application car plus robuste ; comme on calcule ce hash
> nous-mêmes, on peut prendre le meilleur.

### 3.2 Qu'est-ce que le fingerprint local ?

Le **fingerprint local** est un petit fichier texte, posé **à côté** du
`languoid.csv`, qui **mémorise l'état de la dernière opération réussie**. Il
sert de « mémoire » pour savoir si quelque chose a bougé depuis.

Il y a **deux** fichiers de mémoire, à côté de
`data/glottolog/languoid.csv` :

#### a) `languoid.csv.zenodo.fingerprint` — mémoire de la **source distante**

```
version=5.3
recordId=18840967
checksum=md5:a90ec97b56abb432dc02ca7b5a838272
```

- `version` : la version de Glottolog demandée (ex. `5.3`).
- `recordId` : l'identifiant du dépôt Zenodo correspondant.
- `checksum` : le **MD5 du zip distant** au moment du dernier téléchargement.

À chaque cycle, on interroge l'API Zenodo, on récupère le checksum distant
**actuel**, et on le compare à celui stocké ici. **S'ils sont identiques → la
release Glottolog n'a pas changé → inutile de télécharger.**

#### b) `languoid.csv.imported.sha256` — mémoire du **CSV importé en base**

```
b328f7d9e2f9298a5bbe70bd9be96a2b7764f54994fa3d22c661e7a4500060c2
```

- Une seule ligne : le **SHA-256 du `languoid.csv`** tel qu'il était lors du
  **dernier import réussi en base**.

À chaque cycle, on recalcule le SHA-256 du CSV présent sur le disque et on le
compare à cette valeur. **S'ils sont identiques → le CSV n'a pas changé depuis
la dernière écriture en base → inutile d'écrire en base.**

> **Pourquoi deux mémoires séparées ?** Parce que les deux questions sont
> indépendantes :
> - « La *source Zenodo* a-t-elle changé ? » → `.zenodo.fingerprint`
> - « Le *CSV local* a-t-il changé ? » → `.imported.sha256`
>
> Exemple concret : on corrige la normalisation des clics (`//Xegwi` → `ǁXegwi`)
> **sans** que Zenodo ne bouge. Le checksum Zenodo est identique (on saute le
> téléchargement), **mais** le CSV régénéré est différent → le SHA-256 change →
> la sync en base se fait quand même. Les deux mémoires évitent de rater ce cas.

---

## 4. Pourquoi la comparaison externe est-elle si rapide ?

Quand tu déclenches un import et que rien n'a changé, voici ce qui se passe
réellement :

1. **Une seule requête HTTP GET** vers `https://zenodo.org/api/records/18840967`.
   La réponse est du **JSON de métadonnées** (quelques Ko), pas le zip. On y lit
   le champ `checksum` du fichier CLDF `.zip`.
2. On compare cette valeur (`md5:...`) à celle du `.zenodo.fingerprint` local.
   C'est une **comparaison de deux chaînes** → instantané.
3. Si égal : **on ne télécharge rien** (on économise ~200 Mo et la décompression).
4. On lit le SHA-256 déjà stocké et on constate que le CSV n'a pas changé →
   **on n'écrit rien en base.**

Autrement dit, la « comparaison extérieure » ne télécharge jamais les données
elles-mêmes : elle télécharge **la fiche descriptive** qui contient déjà
l'empreinte calculée par Zenodo. C'est Zenodo qui a fait le travail de hachage à
la publication ; nous, on ne fait que **lire** et **comparer** deux courtes
chaînes.

> Code correspondant : `fetchRemoteFingerprint()` fait le GET et extrait le
> checksum ; `downloadAndSync()` calcule `skipDownload` à partir de la comparaison
> `remoteFingerprint.equals(localFingerprint)`.

---

## 5. Le processus complet, étape par étape

Point d'entrée : `GlottologUpdateService.downloadAndSync(...)`.

### Étape 0 — Vérification préalable
`assertUpdateEnabled()` : si l'update est désactivé côté serveur, on renvoie
`503 SERVICE_UNAVAILABLE`. On prépare les dossiers de sortie et de cache.

### Étape 1 — Check Zenodo (stage `CHECKING_REMOTE`)
- `fetchRemoteFingerprintQuietly(version)` interroge l'API Zenodo.
  - `resolveZenodoRecordId()` : le `recordId` est connu d'avance pour les
    versions listées dans `KNOWN_ZENODO_RECORDS` (ex. `5.3 → 18840967`) ; sinon
    on fait une recherche par titre sur l'API.
  - On parcourt les fichiers du dépôt, on prend le `.zip` qui contient `cldf`, et
    on lit son `checksum`.
  - « Quietly » = si le réseau ou l'API est indisponible, on renvoie `null` au
    lieu de planter : on **retombe** simplement sur le flux classique (download).
- On lit le `.zenodo.fingerprint` local.
- **Décision `skipDownload`** = remote non nul **ET** égal au local **ET** le CSV
  existe et n'est pas vide.

### Étape 2 — Téléchargement (stage `DOWNLOADING`, seulement si `!skipDownload`)
- On lance `scripts/download_glottolog_languoid.py` via `ProcessBuilder`, avec
  `--output`, `--cache-dir`, `--version`.
- Chaque ligne de sortie du script est renvoyée en direct à l'UI (logs +
  progression estimée de 8 % à ~40 %).
- **Le script fait AUSSI sa propre vérification Zenodo** (voir §7) : même si le
  backend décide de lancer le script, le script peut répondre
  `GLOTTOLOG_SOURCE_UNCHANGED=1` et ne rien retélécharger si son propre
  fingerprint concorde.
- Si le CSV final est manquant ou vide → `502 BAD_GATEWAY`.

### Étape 3 — Comparaison avec le dernier import (stage `COMPARING_SOURCE`)
- `previewFromCsv()` lit le CSV et calcule un aperçu (lignes source, lignes
  retenues, nombre actuel en base).
- `newHash = sha256Hex(csv)` et `previousHash = readImportedHash(csv)`.
- **Décision `skipSync`** = `previousHash` existe **ET** égal à `newHash` **ET**
  la base contient déjà des données (`databaseCount > 0`).
  - Si vrai : on réécrit les fingerprints (pour garder la mémoire à jour) et on
    renvoie un `unchangedResult` (stage `SOURCE_UNCHANGED`, 100 %). **Aucune
    écriture en base.**

### Étape 4 — Synchronisation (stages `PREPARING_SYNC` → `WRITING_DATABASE`)
`LanguageImportService.syncFromCsv(...)` :
1. **Lecture + normalisation** de chaque ligne (voir §6).
2. **Sélection** des langues à garder (`selectForImport`, voir §6.2).
3. Pour chaque langue retenue, comparaison avec la base :
   - nom déjà pris par une autre entrée → on **désambiguïse** (`Nom (id)`) ;
   - données identiques (`sameGlottologData`) → **unchanged** ;
   - absente en base → **insert** ;
   - présente mais différente → **update**.
4. `flush()`, insertion en lot des nouvelles (`persistNewInChunks`).
5. **Suppression des familles orphelines** (`deleteOrphanFamilies`) : les
   familles encore en base mais absentes du set retenu sont détachées de leurs
   références puis supprimées.
6. Retour d'un `GlottologSyncResultDto` : lignes source, retenues, insérées,
   mises à jour, inchangées, total en base, durée.

### Étape 5 — Mémorisation (fin)
- `writeImportedHash(csv, newHash)` : on met à jour `.imported.sha256`.
- `writeZenodoFingerprint(...)` : on met à jour `.zenodo.fingerprint`.
  - Cas particulier : si l'API Zenodo était indisponible en début de cycle mais
    que la sync a réussi, on **retente** un `fetchRemoteFingerprintQuietly` pour
    rafraîchir la mémoire distante.
- Stage `COMPLETED`, 100 %.

---

## 6. Ce que fait la synchronisation en détail

### 6.1 Normalisation des noms
Avant comparaison/écriture, chaque nom de langue est normalisé
(`normalizeLanguageName`) pour éviter les artefacts d'orthographe ASCII :

- **Clics khoïsan** (ASCII → Unicode) :
  - `//` → `ǁ`  (ex. `//Xegwi` → `ǁXegwi`)
  - `/`  → `ǀ`
  - `=/` → `ǂ`
  - `!`  → `ǃ`
- **Coup de glotte** : l'apostrophe ASCII `'` marquant un coup de glotte devient
  le saltillo `ꞌ` (ex. `'Are'are` → `ꞌAreꞌare`), **en préservant** les
  possessifs anglais (`Bird's`) et les élisions françaises (`d'Ivoire`).

> Cette normalisation existe **des deux côtés** : dans le script Python (pour que
> le CSV soit déjà propre) **et** dans `LanguageImportService` (filet de sécurité
> pour les CSV plus anciens). C'est aussi la raison d'être des deux mémoires
> séparées (§3.2) : une correction de normalisation modifie le CSV sans modifier
> la source Zenodo.

### 6.2 Sélection des langues (`selectForImport` / `select_for_import`)
On ne garde pas tout Glottolog. On garde :
- les **langues et dialectes ayant au moins un pays** ;
- **toutes leurs familles ancêtres** (remontée des chaînes `family_id` et
  `parent_id`), pour permettre le regroupement sur la carte et dans le catalogue.

On exclut le reste (dialectes sans pays, familles orphelines, etc.). Le script
Python applique **exactement la même logique** que le backend Java, pour que le
CSV livré et ce que la base attend soient cohérents.

### 6.3 Suppression des familles orphelines
Après l'écriture, `deleteOrphanFamilies` :
1. liste les familles en base absentes du set retenu ;
2. **détache** toutes les références (`familyId` / `parentId`) qui pointent vers
   ces orphelines — y compris entre orphelines — pour ne pas violer les
   contraintes ;
3. supprime les familles orphelines.

---

## 7. La double vérification côté script Python

`scripts/download_glottolog_languoid.py` ne se contente pas d'obéir : il
refait **sa propre** optimisation avant tout gros travail.

1. `fetch_zenodo_fingerprint(...)` : petit appel API Zenodo (métadonnées).
2. `read_local_fingerprint(output)` : lit le `.zenodo.fingerprint`.
3. Si `version`, `recordId`, `checksum` concordent **et** que le CSV existe et
   n'est pas vide → il imprime `GLOTTOLOG_SOURCE_UNCHANGED=1` et **s'arrête sans
   rien télécharger**.
4. Sinon : téléchargement (`download_zip`), décompression, lecture des CSV CLDF,
   `build_languoid_rows`, filtrage (`select_for_import`), écriture du CSV, puis
   `write_local_fingerprint(...)`.

Il y a donc **deux gardes-fous Zenodo** (backend + script). Le backend décide
souvent de ne même pas lancer le script (`skipDownload`) ; mais si le script est
lancé quand même, il a son propre court-circuit.

---

## 8. Tous les cas possibles

| # | Situation | Zenodo checksum | SHA-256 du CSV | Résultat |
|---|-----------|-----------------|----------------|----------|
| 1 | **Rien n'a changé** | identique | identique | Aucun download, aucune écriture DB. Stage `SOURCE_UNCHANGED`. Réponse en ms. |
| 2 | **Nouvelle release Glottolog** | différent | (recalculé, différent) | Download + décompression + régénération CSV + sync complète en base. |
| 3 | **Correction locale du CSV** (ex. normalisation clics) | identique | différent | `skipDownload = true`, **mais** sync en base car le CSV a changé. |
| 4 | **Base vide** (`databaseCount = 0`) | quelconque | quelconque | On force la sync même si les hash concordent (rien à « sauter » : la base doit être peuplée). |
| 5 | **API Zenodo indisponible** au début | inconnu (`null`) | — | `skipDownload = false` → on lance le script (qui peut lui-même court-circuiter). Après succès, on **retente** de lire le checksum pour rafraîchir la mémoire. |
| 6 | **Script relancé, source inchangée** | identique | identique | Le script imprime `GLOTTOLOG_SOURCE_UNCHANGED=1` ; le backend voit un CSV inchangé → `unchangedResult`. |
| 7 | **CSV manquant / vide après download** | — | — | Erreur `502 BAD_GATEWAY`. |
| 8 | **Update désactivé sur le serveur** | — | — | Erreur `503 SERVICE_UNAVAILABLE` dès le départ. |
| 9 | **Familles devenues orphelines** | — | — | Détachées puis supprimées en fin de sync. |

---

## 9. Déclenchement : manuel et automatique

### Manuel (admin)
L'admin lance un import depuis le dashboard → `GlottologUpdateJobService.startJob(user)` :
- refuse de démarrer un 2ᵉ job si un est déjà en cours (un seul thread dédié) ;
- enregistre une ligne d'historique `RUNNING` (`recordUpdateStarted`) ;
- exécute `downloadAndSync` en tâche de fond et publie la progression (stages +
  logs, max 12 lignes conservées) ;
- à la fin : `recordUpdateSucceeded` ou `recordUpdateFailed`.

### Automatique (planifié)
Dans **Schedule**, l'admin active l'auto-update et choisit une fréquence
(2 semaines, 1 mois, …). `GlottologScheduledUpdateService.runDueUpdate()`
vérifie périodiquement (toutes les heures) si
`dernier_succès + fréquence ≤ maintenant` ; si oui, il lance
`startJob("system:auto-glottolog")` — le même job que le bouton manuel.

---

## 10. Fichiers de référence

| Fichier | Rôle |
|---------|------|
| `scripts/download_glottolog_languoid.py` | Download + filtrage + génération du CSV + court-circuit Zenodo. |
| `src/main/java/org/titiplex/service/GlottologUpdateService.java` | Orchestration, checks checksum/hash, décisions skip. |
| `src/main/java/org/titiplex/service/LanguageImportService.java` | Lecture CSV, normalisation, sélection, écriture DB. |
| `src/main/java/org/titiplex/service/GlottologUpdateJobService.java` | Exécution asynchrone + progression. |
| `src/main/java/org/titiplex/service/GlottologScheduledUpdateService.java` | Déclenchement planifié. |
| `src/main/java/org/titiplex/service/GlottologAdminService.java` | Réglages + historique. |
| `data/glottolog/languoid.csv` | Le CSV filtré courant. |
| `data/glottolog/languoid.csv.zenodo.fingerprint` | Mémoire de la source distante (checksum MD5 Zenodo). |
| `data/glottolog/languoid.csv.imported.sha256` | Mémoire du dernier CSV importé (SHA-256). |
