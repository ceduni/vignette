const BASE_KEY = "vignette:unclaimed-draft-audios";
const ANONYMOUS_KEY = `${BASE_KEY}:anonymous`;

export function draftAudioStorageKey(username) {
    return username ? `${BASE_KEY}:${username}` : ANONYMOUS_KEY;
}

export function migrateLegacyDraftAudios() {
    try {
        const legacyRaw = localStorage.getItem(BASE_KEY);
        if (!legacyRaw) return;

        const legacyDrafts = JSON.parse(legacyRaw);
        if (!Array.isArray(legacyDrafts) || !legacyDrafts.length) {
            localStorage.removeItem(BASE_KEY);
            return;
        }

        const existingRaw = localStorage.getItem(ANONYMOUS_KEY);
        const existingDrafts = existingRaw ? JSON.parse(existingRaw) : [];
        const existingIds = new Set(existingDrafts.map((d) => d.id));
        const merged = [...legacyDrafts.filter((d) => !existingIds.has(d.id)), ...existingDrafts];

        localStorage.setItem(ANONYMOUS_KEY, JSON.stringify(merged));
        localStorage.removeItem(BASE_KEY);
    } catch (e) {
        console.warn("Failed to migrate legacy draft audios", e);
    }
}

export function migrateAnonymousDraftAudios(username) {
    if (!username) return;
    try {
        const anonymousRaw = localStorage.getItem(ANONYMOUS_KEY);
        if (!anonymousRaw) return;

        const anonymousDrafts = JSON.parse(anonymousRaw);
        if (!Array.isArray(anonymousDrafts) || !anonymousDrafts.length) {
            localStorage.removeItem(ANONYMOUS_KEY);
            return;
        }

        const userKey = draftAudioStorageKey(username);
        const existingRaw = localStorage.getItem(userKey);
        const existingDrafts = existingRaw ? JSON.parse(existingRaw) : [];
        const existingIds = new Set(existingDrafts.map((d) => d.id));
        const merged = [...anonymousDrafts.filter((d) => !existingIds.has(d.id)), ...existingDrafts];

        localStorage.setItem(userKey, JSON.stringify(merged));
        localStorage.removeItem(ANONYMOUS_KEY);
        window.dispatchEvent(new CustomEvent("vignette:draft-audios-changed", {detail: merged}));
    } catch (e) {
        console.warn("Failed to migrate anonymous draft audios", e);
    }
}
