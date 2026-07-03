import {computed, ref} from "vue";
import {login as apiLogin, logout as apiLogout, me as apiMe, refresh as apiRefresh} from "../api/auth";
import {getAccessToken} from "../api/rest";

const currentUser = ref(null);
const authLoaded = ref(false);
const authLoading = ref(false);

let loadPromise = null;

export function useAuth() {
    const isAuthenticated = computed(() => !!currentUser.value);
    const roles = computed(() => currentUser.value?.roles ?? []);
    const isAdmin = computed(() => roles.value.includes("ROLE_ADMIN"));

    function hasRole(role) {
        return roles.value.includes(role);
    }

    async function loadMe(force = false) {
        if (loadPromise) return loadPromise;
        if (authLoaded.value && !force) return currentUser.value;

        loadPromise = (async () => {
            authLoading.value = true;
            try {
                currentUser.value = await apiMe();
                if (currentUser.value && !getAccessToken()) {
                    await apiRefresh();
                }
                return currentUser.value;
            } catch {
                currentUser.value = null;
                return null;
            } finally {
                authLoaded.value = true;
                authLoading.value = false;
                loadPromise = null;
            }
        })();

        return loadPromise;
    }

    async function login(username, password) {
        await apiLogin(username, password);
        await loadMe(true);
    }

    async function logout() {
        try {
            await apiLogout();
        } finally {
            currentUser.value = null;
            authLoaded.value = true;
        }
    }

    return {
        currentUser,
        authLoaded,
        authLoading,
        isAuthenticated,
        roles,
        isAdmin,
        hasRole,
        loadMe,
        login,
        logout,
    };
}