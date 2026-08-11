#!/usr/bin/env python3
"""
Télécharge les données Glottolog et produit le fichier languoid.csv pour Vignette.

Glottolog ne propose pas directement un fichier languoid.csv.
On récupère donc la release officielle CLDF sur Zenodo (languages.csv + values.csv),
on la convertit au format attendu par le backend Java, puis on écrit le CSV final.

Par défaut, on n'exporte que les langues utiles à Vignette (~14 000 lignes) :
langues et dialectes avec au moins un pays, plus leurs familles ancêtres pour le regroupement.
(Même logique que LanguageImportService.selectForImport côté Java.)

Usage :
    python3 scripts/download_glottolog_languoid.py
    python3 scripts/download_glottolog_languoid.py --all
    python3 scripts/download_glottolog_languoid.py --version 5.3 --output /tmp/languoid.csv
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from collections import defaultdict
from pathlib import Path

# Famille Glottolog « fourre-tout » pour les langues retirées / douteuses.
# Si une langue est rattachée à cette famille, on la marque bookkeeping=True.
BOOKKEEPING_FAMILY = "book1242"

# Identifiants Zenodo connus pour chaque version CLDF.
# Si la version n'est pas listée ici, on tente une recherche automatique sur Zenodo.
CLDF_ZENODO_RECORDS: dict[str, int] = {
    "5.3": 18840967,
}

# Colonnes du CSV final — doit rester aligné avec le modèle Language du backend.
FIELDNAMES = [
    "id",
    "family_id",
    "parent_id",
    "name",
    "bookkeeping",
    "level",
    "latitude",
    "longitude",
    "iso639_p3code",
    "description",
    "markup_description",
    "child_family_count",
    "child_language_count",
    "child_dialect_count",
    "country_ids",
]

# Emplacement par défaut si on lance le script à la main (sans le backend).
DEFAULT_OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "src/main/resources/glottolog/languoid.csv"
)


def parse_args() -> argparse.Namespace:
    """Lit les options en ligne de commande."""
    parser = argparse.ArgumentParser(
        description="Télécharge Glottolog (CLDF) et génère languoid.csv."
    )
    parser.add_argument(
        "--version",
        default="5.3",
        help="Version Glottolog CLDF à télécharger (défaut : 5.3).",
    )
    parser.add_argument(
        "--zenodo-id",
        type=int,
        help="Forcer un identifiant Zenodo précis (si la recherche auto échoue).",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"Chemin du CSV de sortie (défaut : {DEFAULT_OUTPUT}).",
    )
    parser.add_argument(
        "--cache-dir",
        type=Path,
        help="Dossier où garder le zip téléchargé (évite de re-télécharger).",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Exporter tout Glottolog (~27k lignes). Par défaut : filtre Vignette (~14k).",
    )
    parser.add_argument(
        "--keep-zip",
        action="store_true",
        help="Garder le zip téléchargé dans le dossier cache.",
    )
    return parser.parse_args()


def resolve_zenodo_record_id(version: str, override: int | None) -> int:
    """Trouve sur Zenodo l'archive zip correspondant à la version demandée."""
    if override is not None:
        return override
    if version in CLDF_ZENODO_RECORDS:
        return CLDF_ZENODO_RECORDS[version]

    # Version inconnue : on cherche sur l'API Zenodo par titre.
    query = urllib.parse.quote(
        f'metadata.title:"Glottolog database {version} as CLDF"'
    )
    url = f"https://zenodo.org/api/records/?q={query}&size=1"
    with urllib.request.urlopen(url, timeout=60) as response:
        payload = json.load(response)

    hits = payload.get("hits", {}).get("hits", [])
    if not hits:
        known = ", ".join(sorted(CLDF_ZENODO_RECORDS))
        raise SystemExit(
            f"Impossible de trouver Glottolog CLDF {version} sur Zenodo. "
            f"Versions connues : {known}. Utilise --zenodo-id pour forcer."
        )
    return int(hits[0]["id"])


def fetch_zenodo_fingerprint(version: str, zenodo_id: int | None) -> tuple[int, str, str]:
    """
    Interroge l'API Zenodo (JSON léger) pour obtenir le checksum du zip CLDF.
    Retourne (record_id, checksum, zip_name).
    """
    record_id = resolve_zenodo_record_id(version, zenodo_id)
    metadata_url = f"https://zenodo.org/api/records/{record_id}"
    with urllib.request.urlopen(metadata_url, timeout=60) as response:
        record = json.load(response)

    for file_info in record.get("files", []):
        key = file_info.get("key", "")
        if key.endswith(".zip") and "cldf" in key.lower():
            checksum = (file_info.get("checksum") or "").strip()
            if not checksum:
                md5 = (file_info.get("checksums") or {}).get("md5")
                if md5:
                    checksum = md5 if str(md5).startswith("md5:") else f"md5:{md5}"
            if not checksum:
                raise SystemExit(f"Checksum Zenodo manquant pour {key}.")
            return record_id, checksum, key

    raise SystemExit(f"Aucun zip CLDF trouvé dans l'enregistrement Zenodo {record_id}.")


def fingerprint_path(output: Path) -> Path:
    return output.with_name(output.name + ".zenodo.fingerprint")


def read_local_fingerprint(output: Path) -> dict[str, str] | None:
    path = fingerprint_path(output)
    if not path.is_file():
        return None
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    if not {"version", "recordId", "checksum"} <= values.keys():
        return None
    return values


def write_local_fingerprint(output: Path, version: str, record_id: int, checksum: str) -> None:
    fingerprint_path(output).write_text(
        f"version={version}\nrecordId={record_id}\nchecksum={checksum}\n",
        encoding="utf-8",
    )


def download_zip(record_id: int, destination: Path, zip_name: str | None = None) -> None:
    """Télécharge le zip CLDF depuis Zenodo (peut prendre 1 à 2 minutes)."""
    if zip_name is None:
        metadata_url = f"https://zenodo.org/api/records/{record_id}"
        with urllib.request.urlopen(metadata_url, timeout=60) as response:
            record = json.load(response)

        for file_info in record.get("files", []):
            key = file_info.get("key", "")
            if key.endswith(".zip") and "cldf" in key.lower():
                zip_name = key
                break

    if zip_name is None:
        raise SystemExit(f"Aucun zip CLDF trouvé dans l'enregistrement Zenodo {record_id}.")

    url = f"https://zenodo.org/api/records/{record_id}/files/{zip_name}/content"
    print(f"Téléchargement de {zip_name} (Zenodo {record_id})...")

    try:
        with urllib.request.urlopen(url, timeout=300) as response, open(
            destination, "wb"
        ) as out:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                out.write(chunk)
    except urllib.error.URLError as exc:
        raise SystemExit(f"Échec du téléchargement : {exc}") from exc

    print(f"Enregistré : {destination} ({destination.stat().st_size:,} octets)")


def find_cldf_dir(extracted_root: Path) -> Path:
    """Repère le dossier cldf/ à l'intérieur du zip décompressé."""
    candidates = list(extracted_root.glob("*/cldf"))
    if not candidates:
        candidates = list(extracted_root.glob("cldf"))
    if len(candidates) != 1:
        raise SystemExit(
            f"Un seul dossier cldf/ attendu dans l'archive, trouvé : {len(candidates)}."
        )
    return candidates[0]


def ensure_required_cldf_files(zip_path: Path, extracted_root: Path, cldf_dir: Path) -> None:
    """
    Vérifie que les fichiers indispensables sont bien présents.
    Si l'extraction précédente était incomplète, on ré-extrait ce qui manque.
    """
    required = ["cldf-metadata.json", "languages.csv", "values.csv"]
    missing = [name for name in required if not (cldf_dir / name).is_file()]
    if not missing:
        return

    print(
        "Extraction incomplète détectée, nouvelle extraction de : "
        + ", ".join(missing)
    )

    with zipfile.ZipFile(zip_path) as archive:
        for filename in missing:
            member = next(
                (name for name in archive.namelist() if name.endswith(f"/cldf/{filename}")),
                None,
            )
            if member is None:
                raise SystemExit(
                    f"L'archive {zip_path} ne contient pas le fichier attendu {filename}."
                )
            archive.extract(member, extracted_root)

    still_missing = [name for name in required if not (cldf_dir / name).is_file()]
    if still_missing:
        raise SystemExit(
            "Extraction toujours incomplète. Fichiers manquants : "
            + ", ".join(still_missing)
        )


def load_classifications(values_csv: Path) -> dict[str, str]:
    """
    Lit values.csv et récupère le chemin de classification de chaque langue.

    Exemple : indo1319/.../macr1271 pour l'anglais.
    On en déduit family_id (racine) et parent_id (parent direct).
    """
    classifications: dict[str, str] = {}
    with open(values_csv, encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            if row.get("Parameter_ID") == "classification":
                value = (row.get("Value") or "").strip()
                if value:
                    classifications[row["Language_ID"]] = value
    return classifications


def format_coordinate(value: str) -> str:
    """Nettoie latitude/longitude : pas de zéros inutiles à la fin."""
    value = (value or "").strip()
    if not value:
        return ""
    try:
        number = float(value)
    except ValueError:
        return value
    text = f"{number:.10f}".rstrip("0").rstrip(".")
    return text


def normalize_click_orthography(name: str) -> str:
    """
    Convertit les clics Khoisan ASCII de Glottolog vers Unicode.
    Ex. //Xegwi → ǁXegwi, /Xam → ǀXam, =/Kx'au → ǂKx'au, !Ui → ǃUi.
    """
    if not name:
        return name
    normalized = name.replace("//", "\u01c1")
    normalized = normalized.replace("=/", "\u01c2")
    normalized = normalized.replace("/=", "\u01c2")
    normalized = normalized.replace("/", "\u01c0")
    normalized = normalized.replace("!", "\u01c3")
    return normalized


_FRENCH_ELISION_APOSTROPHE = re.compile(
    r"(?i)\b([dljtncs])'(?=[AEIOUÀÂÄÆÉÈÊËÏÎÔŒÙÛÜÁÉÍÓÚỲŸ])"
)
_ENGLISH_POSSESSIVE_APOSTROPHE = re.compile(r"(?<=[A-Za-z])'s\b")


def normalize_glottal_apostrophes(name: str) -> str:
    """
    Convertit les apostrophes ASCII utilisées comme coups de glotte en saltillo.
    Ex. 'Are'are → ꞌAreꞌare. Préserve d'Ivoire, Bird's, etc.
    """
    if not name or "'" not in name:
        return name
    holders: list[str] = []

    def hold(match: re.Match[str]) -> str:
        holders.append(match.group(0))
        return f"\ue000{len(holders) - 1}\ue001"

    protected = _FRENCH_ELISION_APOSTROPHE.sub(hold, name)
    protected = _ENGLISH_POSSESSIVE_APOSTROPHE.sub(hold, protected)
    normalized = protected.replace("'", "\ua78c")
    for index, value in enumerate(holders):
        normalized = normalized.replace(f"\ue000{index}\ue001", value)
    return normalized


def normalize_language_name(name: str) -> str:
    """Normalise clics Khoisan puis coups de glotte ASCII."""
    return normalize_glottal_apostrophes(normalize_click_orthography(name))


def build_languoid_rows(cldf_dir: Path) -> list[dict[str, str]]:
    """
    Convertit les CSV CLDF au format languoid.csv de Vignette.

    Étape 1 : une ligne par langue/dialecte/famille Glottolog.
    Étape 2 : compte les descendants (familles, langues, dialectes) pour chaque nœud.
    """
    classifications = load_classifications(cldf_dir / "values.csv")

    rows: list[dict[str, str]] = []
    # Chemin complet de chaque langue dans l'arbre (ex. [indo1319, ..., stan1293])
    full_paths: dict[str, list[str]] = {}

    with open(cldf_dir / "languages.csv", encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            languoid_id = (row.get("Glottocode") or row.get("ID") or "").strip()
            if not languoid_id:
                continue

            path_parts = [
                part
                for part in classifications.get(languoid_id, "").split("/")
                if part
            ]
            full_paths[languoid_id] = path_parts + [languoid_id]

            if path_parts:
                family_id = path_parts[0]       # racine de l'arbre (ex. indo-européen)
                parent_id = path_parts[-1]        # parent direct dans la classification
            else:
                # Famille de premier niveau (ex. indo-européen lui-même)
                family_id = ""
                parent_id = ""

            family_column = (row.get("Family_ID") or "").strip()
            bookkeeping = (
                family_column == BOOKKEEPING_FAMILY and languoid_id != BOOKKEEPING_FAMILY
            )

            # Glottolog sépare les pays par « ; », Vignette attend des espaces.
            countries = (row.get("Countries") or "").replace(";", " ").strip()
            level = (row.get("Level") or "").strip().lower()

            rows.append(
                {
                    "id": languoid_id,
                    "family_id": family_id,
                    "parent_id": parent_id,
                    "name": normalize_language_name(row.get("Name") or ""),
                    "bookkeeping": "True" if bookkeeping else "False",
                    "level": level,
                    "latitude": format_coordinate(row.get("Latitude") or ""),
                    "longitude": format_coordinate(row.get("Longitude") or ""),
                    "iso639_p3code": (row.get("ISO639P3code") or "").strip(),
                    "description": "",
                    "markup_description": "",
                    "child_family_count": "0",
                    "child_language_count": "0",
                    "child_dialect_count": "0",
                    "country_ids": countries,
                }
            )

    # Compte combien de descendants chaque langue/famille a, par niveau.
    descendant_counts: dict[str, dict[str, int]] = defaultdict(
        lambda: {"family": 0, "language": 0, "dialect": 0}
    )
    for row in rows:
        level = row["level"]
        if level not in ("family", "language", "dialect"):
            continue
        for ancestor_id in full_paths[row["id"]][:-1]:
            descendant_counts[ancestor_id][level] += 1

    for row in rows:
        counts = descendant_counts[row["id"]]
        row["child_family_count"] = str(counts["family"])
        row["child_language_count"] = str(counts["language"])
        row["child_dialect_count"] = str(counts["dialect"])

    rows.sort(key=lambda item: item["id"])
    return rows


def is_language_or_dialect(row: dict[str, str]) -> bool:
    return row["level"] in ("language", "dialect")


def has_country_ids(row: dict[str, str]) -> bool:
    """Une langue sans pays ne sert pas à la carte ni au catalogue Vignette."""
    return bool((row.get("country_ids") or "").strip())


def follow_family_chain(
    start_id: str,
    by_id: dict[str, dict[str, str]],
    ordered_ids: list[str],
    seen: set[str],
) -> None:
    """Remonte la chaîne des family_id pour inclure les familles liées."""
    visited: set[str] = set()
    current = (start_id or "").strip()
    while current and current not in visited:
        visited.add(current)
        if current not in seen:
            seen.add(current)
            ordered_ids.append(current)
        node = by_id.get(current)
        if node is None:
            break
        current = (node.get("family_id") or "").strip()


def follow_parent_chain(
    start_id: str,
    by_id: dict[str, dict[str, str]],
    ordered_ids: list[str],
    seen: set[str],
) -> None:
    """Remonte la chaîne des parent_id pour inclure les ancêtres de regroupement."""
    visited: set[str] = set()
    current = (start_id or "").strip()
    while current and current not in visited:
        visited.add(current)
        if current not in seen:
            seen.add(current)
            ordered_ids.append(current)
        node = by_id.get(current)
        if node is None:
            break
        current = (node.get("parent_id") or "").strip()


def select_for_import(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    """
    Filtre les lignes comme le fait le backend Java (LanguageImportService).

    On garde :
    - les langues et dialectes qui ont au moins un pays ;
    - leurs familles ancêtres (pour le regroupement sur la carte et dans le catalogue).

    On exclut le reste (dialectes sans pays, familles orphelines, etc.).
    """
    by_id: dict[str, dict[str, str]] = {}
    for row in rows:
        languoid_id = (row.get("id") or "").strip()
        if languoid_id and languoid_id not in by_id:
            by_id[languoid_id] = row

    ordered_ids: list[str] = []
    seen: set[str] = set()

    for row in rows:
        if not is_language_or_dialect(row) or not has_country_ids(row):
            continue
        languoid_id = row["id"]
        if languoid_id not in seen:
            seen.add(languoid_id)
            ordered_ids.append(languoid_id)
        follow_family_chain(row.get("family_id") or "", by_id, ordered_ids, seen)
        follow_parent_chain(row.get("parent_id") or "", by_id, ordered_ids, seen)

    return [by_id[languoid_id] for languoid_id in ordered_ids if languoid_id in by_id]


def write_languoid_csv(rows: list[dict[str, str]], output: Path) -> None:
    """Écrit le CSV final sur disque."""
    output.parent.mkdir(parents=True, exist_ok=True)
    with open(output, "w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDNAMES, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print(f"{len(rows):,} lignes écrites dans {output}")


def main() -> int:
    args = parse_args()

    # D'abord : petit appel API Zenodo (pas de gros téléchargement).
    # Si la release est la même qu'au dernier import et que le CSV existe déjà,
    # on s'arrête tout de suite.
    record_id, remote_checksum, zip_name = fetch_zenodo_fingerprint(
        args.version, args.zenodo_id
    )
    local = read_local_fingerprint(args.output)
    if (
        local is not None
        and local.get("version") == args.version
        and local.get("recordId") == str(record_id)
        and local.get("checksum") == remote_checksum
        and args.output.is_file()
        and args.output.stat().st_size > 0
    ):
        print(
            "GLOTTOLOG_SOURCE_UNCHANGED=1 "
            f"(Zenodo {record_id} inchangé — pas de téléchargement)."
        )
        return 0

    # Dossier cache : soit celui fourni par le backend, soit un dossier temporaire.
    cache_dir = args.cache_dir
    temp_dir_obj = None
    if cache_dir is None:
        temp_dir_obj = tempfile.TemporaryDirectory(prefix="glottolog-cldf-")
        cache_dir = Path(temp_dir_obj.name)

    cache_dir.mkdir(parents=True, exist_ok=True)
    zip_path = cache_dir / f"glottolog-cldf-v{args.version}.zip"

    if not zip_path.exists():
        download_zip(record_id, zip_path, zip_name)
    else:
        print(f"Zip déjà en cache : {zip_path}")

    extract_dir = cache_dir / f"extracted-v{args.version}"
    if not extract_dir.exists():
        extract_dir.mkdir(parents=True, exist_ok=True)
        print(f"Décompression de {zip_path}...")
        with zipfile.ZipFile(zip_path) as archive:
            archive.extractall(extract_dir)

    cldf_dir = find_cldf_dir(extract_dir)
    ensure_required_cldf_files(zip_path, extract_dir, cldf_dir)
    all_rows = build_languoid_rows(cldf_dir)

    if args.all:
        rows = all_rows
        print(f"Export complet : {len(rows):,} langues (--all).")
    else:
        rows = select_for_import(all_rows)
        print(
            f"Filtrage : {len(all_rows):,} lignes sources → {len(rows):,} lignes retenues "
            "(langues/dialectes avec pays + familles ancêtres)."
        )

    write_languoid_csv(rows, args.output)
    write_local_fingerprint(args.output, args.version, record_id, remote_checksum)

    if temp_dir_obj is not None and not args.keep_zip:
        temp_dir_obj.cleanup()

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\nInterrompu.", file=sys.stderr)
        raise SystemExit(130)
