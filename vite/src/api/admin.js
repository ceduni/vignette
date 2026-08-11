import {apiFetch} from "./rest";

export function fetchAdminOverview() {
    return apiFetch("/api/admin/overview");
}

export function fetchAdminUsers() {
    return apiFetch("/api/admin/users");
}

export function updateAdminUserRoles(id, roles) {
    return apiFetch(`/api/admin/users/${id}/roles`, {
        method: "PATCH",
        body: {roles},
    });
}

export function fetchAdminScenarios() {
    return apiFetch("/api/admin/scenarios");
}

export function updateAdminScenarioVisibility(id, visibilityStatus) {
    return apiFetch(`/api/admin/scenarios/${id}/visibility`, {
        method: "PATCH",
        body: {visibilityStatus},
    });
}

export function fetchGlottologPreview() {
    return apiFetch("/api/admin/glottolog/preview");
}

export function updateGlottologLanguages() {
    return apiFetch("/api/admin/glottolog/update", {
        method: "POST",
    });
}

export function startGlottologUpdateJob() {
    return apiFetch("/api/admin/glottolog/update/start", {
        method: "POST",
    });
}

export function fetchGlottologUpdateStatus() {
    return apiFetch("/api/admin/glottolog/update/status");
}

export function fetchGlottologAdminSettings() {
    return apiFetch("/api/admin/glottolog/settings");
}

export function updateGlottologAdminSettings(body) {
    return apiFetch("/api/admin/glottolog/settings", {
        method: "PUT",
        body,
    });
}

export function fetchGlottologUpdateHistory() {
    return apiFetch("/api/admin/glottolog/history");
}

export function fetchGlottologNotifications() {
    return apiFetch("/api/admin/glottolog/notifications");
}

export function fetchGlottologUpdateRequests() {
    return apiFetch("/api/admin/glottolog/update/requests");
}
