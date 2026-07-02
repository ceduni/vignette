import {apiFetch} from "./rest";

function normalizeListParams(input, page = 0, size = 50) {
    if (input instanceof URLSearchParams) {
        return input;
    }

    const params = new URLSearchParams();
    params.set("q", input ?? "");
    params.set("page", String(page));
    params.set("size", String(size));
    return params;
}

function normalizePageResponse(data) {
    return {
        content: data?.content ?? [],
        number: data?.page?.number ?? data?.number ?? 0,
        totalPages: data?.page?.totalPages ?? data?.totalPages ?? 0,
        totalElements: data?.page?.totalElements ?? data?.totalElements ?? 0,
        size: data?.page?.size ?? data?.size ?? 0,
    };
}

export async function fetchLanguages(input = "", page = 0, size = 50) {
    const params = normalizeListParams(input, page, size);
    const data = await apiFetch(`/api/languages?${params.toString()}`);
    return normalizePageResponse(data);
}

export function fetchLanguage(id) {
    return apiFetch(`/api/languages/${id}`);
}

export function fetchLanguageScenarios(id) {
    return apiFetch(`/api/languages/${id}/scenarios`);
}

export function fetchLanguagePreviewAudio(id) {
    return apiFetch(`/api/languages/${id}/preview-audio`);
}

export function fetchLanguageOptions(params) {
    return apiFetch(`/api/languages/options?${params.toString()}`);
}

export function fetchMyLanguagePermissions(id) {
    return apiFetch(`/api/languages/${id}/permissions/me`);
}

export function updateLanguage(id, body) {
    return apiFetch(`/api/languages/${id}`, {
        method: "PUT",
        body,
    });
}