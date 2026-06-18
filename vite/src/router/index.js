import {createRouter, createWebHistory} from "vue-router";

import HomeView from "../views/HomeView.vue";
import LanguagesView from "../views/LanguagesView.vue";
import LanguageDetailView from "../views/LanguageDetailView.vue";
import LanguageWorldMapView from "../views/languages/LanguageWorldMapView.vue";
import LoginView from "../views/LoginView.vue";
import RegisterView from "../views/RegisterView.vue";
import ScenariosView from "../views/ScenariosView.vue";
import ScenarioDetailView from "../views/ScenarioDetailView.vue";
import CreateScenarioView from "../views/CreateScenarioView.vue";
import UserView from "../views/UserView.vue";
import WorkspaceHomeView from "../views/WorkspaceHomeView.vue";
import ScenarioManageView from "../views/ScenarioManageView.vue";
import AdminDashboardView from "../views/AdminDashboardView.vue";
import AdminCommunityView from "../views/AdminCommunityView.vue";
import AdminLanguagesView from "../views/AdminLanguagesView.vue";

import {useAuth} from "../composables/useAuth";
import AboutProjectView from "../views/AboutProjectView.vue";
import AdminScenariosView from "@/views/AdminScenariosView.vue";
import AdminUsersView from "@/views/AdminUsersView.vue";

const routes = [
    {
        path: "/",
        name: "home",
        component: HomeView,
        meta: { title: "Home", description: "Landing page for the Vignette project and platform overview." },
    },
    {
        path: "/about",
        name: "about",
        component: AboutProjectView,
        meta: { title: "About the project", description: "Origins, goals and design rationale of the Vignette project." },
    },
    {
        path: "/languages",
        name: "languages",
        component: LanguagesView,
        meta: {
            title: "Languages",
            description: "Browse and search language entries.",
        },
    },
    {
        path: "/languages/map",
        name: "languages-map",
        component: LanguageWorldMapView,
        meta: {
            title: "Language map",
            description: "Interactive world map with per-country language data.",
        },
    },
    {
        path: "/languages/:id",
        name: "language-detail",
        component: LanguageDetailView,
        props: true,
        meta: {
            title: "Language details",
            description: "Detailed language entry with metadata, scenarios and discussion.",
        },
    },
    {
        path: "/login",
        name: "login",
        component: LoginView,
        meta: { guestOnly: true, title: "Login", description: "Authenticate to access protected features." },
    },
    {
        path: "/register",
        name: "register",
        component: RegisterView,
        meta: { guestOnly: true, title: "Register", description: "Create a new Vignette user account." },
    },
    {
        path: "/scenarios",
        name: "scenarios",
        component: ScenariosView,
        meta: { title: "Scenarios", description: "Browse all published scenarios from the community." },
    },
    {
        path: "/scenarios/:id",
        name: "scenario-detail",
        component: ScenarioDetailView,
        props: true,
        meta: { title: "Scenario workspace", description: "Detailed scenario page with storyboard, playback and media panels." },
    },
    {
        path: "/create-scenario",
        name: "create-scenario",
        component: CreateScenarioView,
        meta: { requiresAuth: true, title: "Create scenario", description: "Create a new scenario and initialize its metadata." },
    },
    {
        path: "/user",
        name: "user",
        component: UserView,
        meta: { requiresAuth: true, title: "My profile", description: "Manage your user profile, affiliations and personal work." },
    },
    {
        path: "/workspace",
        name: "workspace",
        component: WorkspaceHomeView,
        meta: { requiresAuth: true, title: "My scenarios", description: "Manage your personal scenarios, drafts and publications." },
    },
    {
        path: "/scenarios/:id/manage",
        name: "scenario-manage",
        component: ScenarioManageView,
        props: true,
        meta: { requiresAuth: true, title: "Manage scenario", description: "Private management interface for scenario settings and community governance." },
    },
    {
        path: "/admin",
        name: "admin",
        component: AdminDashboardView,
        meta: { requiresAuth: true, requiresAdmin: true, title: "Admin dashboard", description: "Global administration and moderation workspace." },
    },
    {
        path: "/admin/community",
        name: "admin-community",
        component: AdminCommunityView,
        meta: { requiresAuth: true, requiresAdmin: true, title: "Community administration", description: "Global accreditation moderation and grants." },
    },
    {
        path: "/admin/languages",
        name: "admin-languages",
        component: AdminLanguagesView,
        meta: { requiresAuth: true, requiresAdmin: true, title: "Language administration", description: "Language-level editing and moderation tools." },
    },
    {
        path: "/admin/users",
        name: "admin-users",
        component: AdminUsersView,
        meta: { requiresAuth: true, requiresAdmin: true, title: "User administration", description: "Manage users and roles." },
    },
    {
        path: "/admin/scenarios",
        name: "admin-scenarios",
        component: AdminScenariosView,
        meta: { requiresAuth: true, requiresAdmin: true, title: "Scenario administration", description: "Manage scenario visibility and moderation." },
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

router.beforeEach(async (to) => {
    const {isAuthenticated, authLoaded, loadMe, hasRole} = useAuth();

    if (!authLoaded.value) {
        await loadMe();
    }

    if (to.meta.requiresAuth && !isAuthenticated.value) {
        return {path: "/login", query: {redirect: to.fullPath}};
    }

    if (to.meta.requiresAdmin && !hasRole("ROLE_ADMIN")) {
        return {path: "/"};
    }

    if (to.meta.guestOnly && isAuthenticated.value) {
        return {path: "/"};
    }

    if (to.meta?.title) {
        document.title = `Vignette · ${to.meta.title}`;
    } else {
        document.title = "Vignette";
    }

    return true;
});

export default router;
