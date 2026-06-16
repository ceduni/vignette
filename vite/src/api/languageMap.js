import {apiFetch} from "./rest";

const DEFAULT_ENDPOINT = "/api/languages/by-country";

function isLanguageRow(value) {
  return value && typeof value === "object";
}

function normalizeLanguageRows(rows) {
  if (!Array.isArray(rows)) return [];

  return rows
      .filter(isLanguageRow)
      .map((row) => ({
        id: String(row.id ?? ""),
        name: String(row.name ?? "Unknown language"),
        level: String(row.level ?? "unknown"),
        family: String(row.family ?? "unknown"),
        parent: String(row.parent ?? "unknown"),
      }));
}

export async function fetchLanguagesByCountry() {
  const endpoint = (import.meta.env.VITE_LANGUAGE_MAP_ENDPOINT || DEFAULT_ENDPOINT).trim() || DEFAULT_ENDPOINT;
  const data = await apiFetch(endpoint);

  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new Error("Invalid payload: expected an object indexed by ISO_A3 codes.");
  }

  return Object.entries(data).reduce((acc, [isoA3, rows]) => {
    const key = String(isoA3 || "").toUpperCase();
    if (!key) return acc;

    acc[key] = normalizeLanguageRows(rows);
    return acc;
  }, {});
}
