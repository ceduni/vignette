<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue";
import {RouterLink, useRoute, useRouter} from "vue-router";
import {useAuth} from "../composables/useAuth";

const route = useRoute();
const router = useRouter();
const {currentUser, isAuthenticated, isAdmin, loadMe, logout} = useAuth();

const mobileMenuOpen = ref(false);
const profileOpen = ref(false);
const profileMenuEl = ref(null);

onMounted(() => {
  loadMe();
  document.addEventListener("click", onDocClick);
});

onBeforeUnmount(() => {
  document.removeEventListener("click", onDocClick);
});

watch(
    () => route.fullPath,
    () => {
      mobileMenuOpen.value = false;
      profileOpen.value = false;
    }
);

const navItems = computed(() => {
  const base = [
    {to: "/", label: "Home"},
    {to: "/languages", label: "Languages"},
    {to: "/scenarios", label: "Scenarios"},
    {to: "/about", label: "About"},
  ];
  if (isAdmin.value) base.splice(4, 0, {to: "/admin", label: "Admin"});
  return base;
});

const initials = computed(() => {
  const name = currentUser.value?.displayName || currentUser.value?.username || "";
  return name.split(/\s+/).map(w => w[0]).slice(0, 2).join("").toUpperCase() || "?";
});

function isActive(path) {
  if (path === "/") return route.path === "/";
  return route.path.startsWith(path);
}

function toggleMobileMenu() {
  mobileMenuOpen.value = !mobileMenuOpen.value;
}

function toggleProfile() {
  profileOpen.value = !profileOpen.value;
}

function onDocClick(e) {
  if (profileMenuEl.value && !profileMenuEl.value.contains(e.target)) {
    profileOpen.value = false;
  }
}

async function doLogout() {
  profileOpen.value = false;
  await logout();
  router.push("/login");
}
</script>

<template>
  <header class="app-header">
    <div class="app-container app-header__inner">
      <RouterLink to="/" class="app-brand">
        <span class="app-brand__title">Vignette</span>
      </RouterLink>

      <button
          type="button"
          class="app-header__burger"
          :aria-expanded="mobileMenuOpen ? 'true' : 'false'"
          aria-label="Toggle navigation"
          @click="toggleMobileMenu"
      >
        <span></span>
        <span></span>
        <span></span>
      </button>

      <div
          class="app-header__panel"
          :class="{ 'is-open': mobileMenuOpen }"
      >
        <nav class="app-nav" aria-label="Primary">
          <RouterLink
              v-for="item in navItems"
              :key="item.to"
              :to="item.to"
              class="app-nav__link"
              :class="{ 'is-active': isActive(item.to) }"
          >
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="app-header__actions">
          <RouterLink
              v-if="isAuthenticated"
              to="/create-scenario"
              class="btn btn--rose"
          >
            Create scenario
          </RouterLink>

          <!-- Profile dropdown -->
          <div v-if="isAuthenticated" class="profile-menu" ref="profileMenuEl">
            <button
                type="button"
                class="profile-trigger"
                :class="{ 'is-open': profileOpen }"
                :aria-expanded="profileOpen"
                aria-label="Open profile menu"
                @click="toggleProfile"
            >
              <span class="profile-trigger__avatar">{{ initials }}</span>
              <span class="profile-trigger__name">{{ currentUser?.username || "Profile" }}</span>
              <svg class="profile-trigger__chevron" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="m6 9 6 6 6-6"/>
              </svg>
            </button>

            <Transition name="pd">
              <div v-if="profileOpen" class="profile-dropdown" role="menu">

                <!-- User info -->
                <div class="pd-identity">
                  <div class="pd-avatar">{{ initials }}</div>
                  <div class="pd-identity__text">
                    <span class="pd-display-name">{{ currentUser?.displayName || currentUser?.username }}</span>
                    <span class="pd-username">@{{ currentUser?.username }}</span>
                  </div>
                </div>

                <div class="pd-divider"/>

                <!-- Profile & content -->
                <div class="pd-section">
                  <RouterLink to="/user" class="pd-item" role="menuitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                         stroke-linecap="round" stroke-linejoin="round">
                      <path d="M20 21a8 8 0 0 0-16 0"/><circle cx="12" cy="8" r="4"/>
                    </svg>
                    My profile
                  </RouterLink>
                  <RouterLink to="/workspace" class="pd-item" role="menuitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                         stroke-linecap="round" stroke-linejoin="round">
                      <rect x="3" y="3" width="7" height="7" rx="1"/>
                      <rect x="14" y="3" width="7" height="7" rx="1"/>
                      <rect x="3" y="14" width="7" height="7" rx="1"/>
                      <rect x="14" y="14" width="7" height="7" rx="1"/>
                    </svg>
                    My scenarios
                  </RouterLink>
                </div>

                <!-- Admin -->
                <template v-if="isAdmin">
                  <div class="pd-divider"/>
                  <div class="pd-section">
                    <RouterLink to="/admin" class="pd-item" role="menuitem">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                           stroke-linecap="round" stroke-linejoin="round">
                        <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                      </svg>
                      Admin panel
                    </RouterLink>
                  </div>
                </template>

                <div class="pd-divider"/>

                <!-- Sign out -->
                <div class="pd-section">
                  <button type="button" class="pd-item pd-item--danger" role="menuitem" @click="doLogout">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                         stroke-linecap="round" stroke-linejoin="round">
                      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                      <polyline points="16 17 21 12 16 7"/>
                      <line x1="21" y1="12" x2="9" y2="12"/>
                    </svg>
                    Sign out
                  </button>
                </div>

              </div>
            </Transition>
          </div>

          <RouterLink
              v-if="!isAuthenticated"
              to="/login"
              class="btn btn--ghost"
          >
            Login
          </RouterLink>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.profile-menu {
  position: relative;
}

.profile-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px 5px 5px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.20);
  background: rgba(255, 255, 255, 0.10);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-weight: 600;
  font-size: 0.9rem;
  transition: background 160ms ease, border-color 160ms ease;
}

.profile-trigger:hover,
.profile-trigger.is-open {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.32);
}

.profile-trigger__avatar {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  background: #F5D4CE;
  color: #5B1928;
  font-size: 0.72rem;
  font-weight: 800;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  letter-spacing: 0.04em;
}

.profile-trigger__name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-trigger__chevron {
  width: 14px;
  height: 14px;
  opacity: 0.7;
  transition: transform 200ms ease;
  flex-shrink: 0;
}

.profile-trigger.is-open .profile-trigger__chevron {
  transform: rotate(180deg);
}

.profile-dropdown {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 230px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(30, 8, 18, 0.14), 0 2px 6px rgba(30, 8, 18, 0.06);
  overflow: hidden;
  z-index: 900;
}

.pd-identity {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 14px 12px;
}

.pd-avatar {
  width: 38px;
  height: 38px;
  border-radius: 999px;
  background: #F5D4CE;
  color: #5B1928;
  font-size: 0.78rem;
  font-weight: 800;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  letter-spacing: 0.04em;
}

.pd-identity__text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.pd-display-name {
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pd-username {
  font-size: 0.76rem;
  color: var(--text-soft);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pd-divider {
  height: 1px;
  background: var(--border);
  margin: 0;
}

.pd-section {
  padding: 6px;
}

.pd-item {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 9px 10px;
  border-radius: 10px;
  border: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 0.88rem;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  transition: background 120ms ease, color 120ms ease;
  text-align: left;
}

.pd-item svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: var(--text-soft);
}

.pd-item:hover {
  background: var(--surface-alt);
  color: var(--text);
}

.pd-item:hover svg {
  color: var(--primary);
}

.pd-item--danger {
  color: var(--danger);
}

.pd-item--danger svg {
  color: var(--danger);
  opacity: 0.7;
}

.pd-item--danger:hover {
  background: #fff1f2;
  color: var(--danger);
}

.pd-enter-active { transition: opacity 160ms ease, transform 160ms ease; }
.pd-leave-active { transition: opacity 120ms ease, transform 120ms ease; }
.pd-enter-from   { opacity: 0; transform: translateY(-6px) scale(0.97); }
.pd-leave-to     { opacity: 0; transform: translateY(-4px) scale(0.97); }
</style>
