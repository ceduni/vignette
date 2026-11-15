const apiLogin = vi.fn();
const apiLogout = vi.fn();
const apiMe = vi.fn();
const apiRefresh = vi.fn();

async function loadComposable() {
    vi.resetModules();

    vi.doMock("@/api/auth", () => ({
        login: apiLogin,
        logout: apiLogout,
        me: apiMe,
        refresh: apiRefresh,
    }));

    return import("@/composables/useAuth");
}

describe("useAuth", () => {
    beforeEach(() => {
        apiLogin.mockReset();
        apiLogout.mockReset();
        apiMe.mockReset();
        apiRefresh.mockReset();
        apiRefresh.mockResolvedValue({accessToken: "refreshed-token"});
    });

    it("loads current user successfully", async () => {
        apiMe.mockResolvedValueOnce({id: 1, username: "titi"});
        const {useAuth} = await loadComposable();

        const auth = useAuth();
        const user = await auth.loadMe();

        expect(user).toEqual({id: 1, username: "titi"});
        expect(auth.currentUser.value).toEqual({id: 1, username: "titi"});
        expect(auth.isAuthenticated.value).toBe(true);
        expect(auth.authLoaded.value).toBe(true);
    });

    it("becomes unauthenticated when me fails", async () => {
        apiMe.mockRejectedValueOnce(new Error("401"));
        const {useAuth} = await loadComposable();

        const auth = useAuth();
        const user = await auth.loadMe();

        expect(user).toBeNull();
        expect(auth.currentUser.value).toBeNull();
        expect(auth.isAuthenticated.value).toBe(false);
        expect(auth.authLoaded.value).toBe(true);
    });

    it("deduplicates concurrent loadMe calls", async () => {
        let resolveMe;
        apiMe.mockImplementationOnce(() => new Promise((resolve) => {
            resolveMe = resolve;
        }));

        const {useAuth} = await loadComposable();
        const auth = useAuth();

        const p1 = auth.loadMe();
        const p2 = auth.loadMe();

        resolveMe({id: 3, username: "shared"});
        const [u1, u2] = await Promise.all([p1, p2]);

        expect(apiMe).toHaveBeenCalledTimes(1);
        expect(u1).toEqual({id: 3, username: "shared"});
        expect(u2).toEqual({id: 3, username: "shared"});
    });

    it("login calls API then reloads current user", async () => {
        apiLogin.mockResolvedValueOnce({accessToken: "token"});
        apiMe.mockResolvedValueOnce({id: 7, username: "after-login"});

        const {useAuth} = await loadComposable();
        const auth = useAuth();

        await auth.login("alice", "secret");

        expect(apiLogin).toHaveBeenCalledWith("alice", "secret");
        expect(apiMe).toHaveBeenCalledTimes(1);
        expect(auth.currentUser.value).toEqual({id: 7, username: "after-login"});
        expect(auth.isAuthenticated.value).toBe(true);
    });

    it("logout clears current user even if API logout fails", async () => {
        apiMe.mockResolvedValueOnce({id: 1, username: "bob"});
        apiLogout.mockRejectedValueOnce(new Error("network"));

        const {useAuth} = await loadComposable();
        const auth = useAuth();

        await auth.loadMe();

        await expect(auth.logout()).rejects.toThrow("network");

        expect(auth.currentUser.value).toBeNull();
        expect(auth.isAuthenticated.value).toBe(false);
        expect(auth.authLoaded.value).toBe(true);
    });
});
