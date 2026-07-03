import {computed, nextTick, ref} from "vue";
import {simpleComponentStub} from "../../helpers/stubs";

async function loadRouterWithAuthState(
    {
        authenticated = false,
        authLoaded = true,
    } = {}) {
    vi.resetModules();

    const state = {
        currentUser: ref(authenticated ? {id: 1, username: "user"} : null),
        authLoaded: ref(authLoaded),
        authLoading: ref(false),
    };

    state.isAuthenticated = computed(() => !!state.currentUser.value);
    state.loadMe = vi.fn(async () => {
        state.authLoaded.value = true;
        return state.currentUser.value;
    });
    state.login = vi.fn();
    state.logout = vi.fn();

    vi.doMock("@/views/HomeView.vue", () => ({default: simpleComponentStub("HomeView")}));
    vi.doMock("@/views/LanguagesView.vue", () => ({default: simpleComponentStub("LanguagesView")}));
    vi.doMock("@/views/languages/LanguageWorldMapView.vue", () => ({default: simpleComponentStub("LanguageWorldMapView")}));
    vi.doMock("@/views/LanguageDetailView.vue", () => ({default: simpleComponentStub("LanguageDetailView")}));
    vi.doMock("@/views/LoginView.vue", () => ({default: simpleComponentStub("LoginView")}));
    vi.doMock("@/views/RegisterView.vue", () => ({default: simpleComponentStub("RegisterView")}));
    vi.doMock("@/views/ScenariosView.vue", () => ({default: simpleComponentStub("ScenariosView")}));
    vi.doMock("@/views/ScenarioDetailView.vue", () => ({default: simpleComponentStub("ScenarioDetailView")}));
    vi.doMock("@/views/CreateScenarioView.vue", () => ({default: simpleComponentStub("CreateScenarioView")}));
    vi.doMock("@/views/UserView.vue", () => ({default: simpleComponentStub("UserView")}));
    vi.doMock("@/views/AboutProjectView.vue", () => ({default: simpleComponentStub("AboutProjectView")}));

    vi.doMock("@/composables/useAuth", () => ({
        useAuth: () => state,
    }));

    const routerModule = await import("@/router/index");
    return {router: routerModule.default, state};
}

describe("router guards", () => {
    beforeEach(() => {
        window.history.replaceState({}, "", "/");
        document.title = "Initial";
    });

    it("redirects unauthenticated users away from protected routes", async () => {
        const {router} = await loadRouterWithAuthState({
            authenticated: false,
            authLoaded: true,
        });

        await router.push("/user");
        await router.isReady();
        await nextTick();

        expect(router.currentRoute.value.path).toBe("/login");
        expect(router.currentRoute.value.query.redirect).toBe("/user");
    });

    it("redirects authenticated users away from guest-only routes", async () => {
        const {router} = await loadRouterWithAuthState({
            authenticated: true,
            authLoaded: true,
        });

        await router.push("/login");
        await router.isReady();
        await nextTick();

        expect(router.currentRoute.value.path).toBe("/");
    });

    it("loads auth state when not yet loaded", async () => {
        const {router, state} = await loadRouterWithAuthState({
            authenticated: false,
            authLoaded: false,
        });

        await router.push("/languages");
        await router.isReady();
        await nextTick();

        expect(state.loadMe).toHaveBeenCalledTimes(1);
    });

    it("updates document title from route meta", async () => {
        const {router} = await loadRouterWithAuthState({
            authenticated: false,
            authLoaded: true,
        });

        await router.push("/languages");
        await router.isReady();
        await nextTick();

        expect(document.title).toBe("Vignette · Languages");
    });
});