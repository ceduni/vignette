let accessToken = sessionStorage.getItem("accessToken") || null;
let refreshPromise = null;

export function setAccessToken(token) {
    accessToken = token;
    if (token) {
        sessionStorage.setItem("accessToken", token);
    } else {
        sessionStorage.removeItem("accessToken");
    }
}

export function getAccessToken() {
    return accessToken;
}

function getCookie(name) {
    return document.cookie
        .split("; ")
        .find((row) => row.startsWith(name + "="))
        ?.split("=")[1];
}

function isAbsoluteUrl(value) {
    return /^https?:\/\//i.test(value);
}

export function buildApiUrl(path) {
    if (!path) return path;
    if (isAbsoluteUrl(path)) return path;

    const normalizedPath = path.startsWith("/") ? path : `/${path}`;
    const apiBase = (import.meta.env.VITE_API_BASE || "").trim();

    if (!apiBase) {
        return normalizedPath;
    }

    const normalizedBase = apiBase.endsWith("/")
        ? apiBase.slice(0, -1)
        : apiBase;

    return `${normalizedBase}${normalizedPath}`;
}

function isSessionAuthPath(path) {
    return path.startsWith("/api/auth/");
}

function shouldAttachBearer(path) {
    return !!accessToken && !isSessionAuthPath(path);
}

async function requestAccessTokenRefresh() {
    if (refreshPromise) return refreshPromise;

    refreshPromise = (async () => {
        try {
            const res = await fetch(buildApiUrl("/api/auth/refresh"), {
                method: "POST",
                headers: {
                    Accept: "application/json",
                },
                credentials: "include",
            });

            if (!res.ok) {
                setAccessToken(null);
                return false;
            }

            const ct = res.headers.get("content-type") || "";
            if (!ct.includes("application/json")) {
                setAccessToken(null);
                return false;
            }

            const data = await res.json();
            const newToken = data?.accessToken ?? data?.token ?? null;

            if (!newToken) {
                setAccessToken(null);
                return false;
            }

            setAccessToken(newToken);
            return true;
        } catch {
            setAccessToken(null);
            return false;
        } finally {
            refreshPromise = null;
        }
    })();

    return refreshPromise;
}

function buildErrorMessage(data, fallback) {
    if (!data) return fallback;
    return data.message || data.error || data.detail || JSON.stringify(data);
}

export async function apiFetch(path, options = {}, meta = {}) {
    const allowRefresh = meta.allowRefresh ?? true;

    const headers = new Headers(options.headers || {});
    headers.set("Accept", "application/json");

    let body = options.body;
    const method = (options.method || "GET").toUpperCase();

    const isFormData = body instanceof FormData;
    const isBlob = body instanceof Blob;
    const isString = typeof body === "string";

    if (body != null && !isFormData && !isBlob && !isString) {
        if (!headers.has("Content-Type")) {
            headers.set("Content-Type", "application/json");
        }
        body = JSON.stringify(body);
    }

    if (shouldAttachBearer(path)) {
        headers.set("Authorization", `Bearer ${accessToken}`);
    }

    const xsrf = getCookie("XSRF-TOKEN");
    const stateChanging = !["GET", "HEAD", "OPTIONS"].includes(method);
    if (stateChanging && xsrf && !headers.has("X-XSRF-TOKEN")) {
        headers.set("X-XSRF-TOKEN", decodeURIComponent(xsrf));
    }

    const res = await fetch(buildApiUrl(path), {
        ...options,
        method,
        headers,
        body,
        credentials: "include",
    });

    if (
        res.status === 401
        && !!accessToken
        && !isSessionAuthPath(path)
    ) {
        if (allowRefresh) {
            const refreshed = await requestAccessTokenRefresh();
            if (refreshed) {
                return apiFetch(path, options, {allowRefresh: false});
            }
        }

        if (meta.allowAnonymousRetry ?? true) {
            setAccessToken(null);
            return apiFetch(path, options, {allowRefresh: false, allowAnonymousRetry: false});
        }
    }

    if (!res.ok) {
        let message = `HTTP ${res.status}`;
        try {
            const ct = res.headers.get("content-type") || "";
            if (ct.includes("application/json")) {
                const data = await res.json();
                message = buildErrorMessage(data, message);
            } else {
                const text = await res.text();
                if (text) message = text;
            }
        } catch {
            // ignore parse issues
        }
        throw new Error(message);
    }

    if (res.status === 204) return null;

    const ct = res.headers.get("content-type") || "";
    if (ct.includes("application/json")) return res.json();
    return res.text();
}