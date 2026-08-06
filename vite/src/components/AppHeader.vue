<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { RouterLink, useRoute, useRouter } from "vue-router";
import { useAuth } from "../composables/useAuth";
import { useNotifications } from "../composables/useNotifications";
import { useLanguageFollows } from "../composables/useLanguageFollows";

const route  = useRoute();
const router = useRouter();
const { currentUser, isAuthenticated, isAdmin, loadMe, logout } = useAuth();
const {
  notifications, unreadCount, hasUnread, grouped,
  init, teardown, markAsRead, markAllAsRead, deleteNotification, deleteAllNotifications,
  iconForType, formatTime,
} = useNotifications();
const { loadFollows, reset: resetFollows } = useLanguageFollows();

const mobileMenuOpen   = ref(false);
const profileOpen      = ref(false);
const notifOpen        = ref(false);
const profileMenuEl    = ref(null);
const notifMenuEl      = ref(null);
const deleteAllConfirm = ref(false);

onMounted(() => {
  loadMe().then(() => {
    if (isAuthenticated.value) {
      init();
      loadFollows();
    }
  });
  document.addEventListener("click", onDocClick);
});

onBeforeUnmount(() => {
  document.removeEventListener("click", onDocClick);
  teardown();
});

watch(() => route.fullPath, () => {
  mobileMenuOpen.value = false;
  profileOpen.value    = false;
  notifOpen.value      = false;
});

// Init notifications when user logs in
watch(isAuthenticated, (v) => {
  if (v) { init(); loadFollows(); }
  else   { teardown(); resetFollows(); }
});

const navItems = computed(() => {
  const base = [
    { to: "/",          label: "Home" },
    { to: "/languages", label: "Languages" },
    { to: "/scenarios", label: "Scenarios" },
    { to: "/about",     label: "About" },
  ];
  if (isAdmin.value) base.splice(4, 0, { to: "/admin", label: "Admin" });
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

function toggleMobileMenu() { mobileMenuOpen.value = !mobileMenuOpen.value; }
function toggleProfile()    { profileOpen.value = !profileOpen.value; notifOpen.value = false; }

function toggleNotif() {
  notifOpen.value = !notifOpen.value;
  profileOpen.value = false;
}

function handleDeleteAll() {
  deleteAllConfirm.value = true;
}

function cancelDeleteAll() {
  deleteAllConfirm.value = false;
}

function confirmDeleteAllNotifications() {
  deleteAllConfirm.value = false;
  deleteAllNotifications();
}

function onDocClick(e) {
  if (profileMenuEl.value && !profileMenuEl.value.contains(e.target)) profileOpen.value = false;
  if (notifMenuEl.value   && !notifMenuEl.value.contains(e.target))   notifOpen.value   = false;
}

async function doLogout() {
  profileOpen.value = false;
  await logout();
  router.push("/login");
}

function handleNotifClick(n) {
  markAsRead(n.id);
  if (n.targetUrl) {
    notifOpen.value = false;
    router.push(n.targetUrl);
  }
}

// Icon SVG per notification type
function notifIconPath(type) {
  switch (type) {
    case "NEW_SCENARIO_IN_FOLLOWED_LANGUAGE":
      return `<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>`;
    case "NEW_LIKE":
      return `<path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>`;
    case "NEW_BOOKMARK":
      return `<path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>`;
    default:
      return `<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>`;
  }
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
        <span></span><span></span><span></span>
      </button>

      <div class="app-header__panel" :class="{ 'is-open': mobileMenuOpen }">
        <nav class="app-nav" aria-label="Primary">
          <RouterLink
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="app-nav__link"
            :class="{ 'is-active': isActive(item.to) }"
          >{{ item.label }}</RouterLink>
        </nav>

        <div class="app-header__actions">
          <RouterLink v-if="isAuthenticated" to="/create-scenario" class="btn btn--rose">
            Create scenario
          </RouterLink>

          <!-- ── Notification bell ───────────────────────────────────── -->
          <div v-if="isAuthenticated" class="notif-menu" ref="notifMenuEl">
            <button
              type="button"
              class="notif-trigger"
              :class="{ 'is-open': notifOpen }"
              :aria-expanded="notifOpen"
              aria-label="Notifications"
              @click="toggleNotif"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
              </svg>
              <span v-if="hasUnread" class="notif-badge">
                {{ unreadCount > 9 ? '9+' : unreadCount }}
              </span>
            </button>

            <Transition name="pd">
              <div v-if="notifOpen" class="notif-dropdown" role="dialog" aria-label="Notifications">

                <!-- Header -->
                <div class="notif-head">
                  <span class="notif-head__title">Notifications</span>
                  <span v-if="hasUnread" class="notif-head__count">{{ unreadCount }} new</span>
                  <div class="notif-head__actions">
                    <button
                      v-if="hasUnread"
                      type="button"
                      class="notif-head__mark-all"
                      @click="markAllAsRead"
                    >Mark all read</button>
                    <button
                      v-if="notifications.length"
                      type="button"
                      class="notif-head__delete-all"
                      @click="handleDeleteAll"
                    >Delete all</button>
                  </div>
                </div>

                <!-- List -->
                <div class="notif-list">
                  <!-- Today -->
                  <template v-if="grouped.today.length">
                    <p class="notif-group-label">Today</p>
                    <div
                      v-for="n in grouped.today"
                      :key="n.id"
                      class="notif-item"
                      :class="{ 'notif-item--unread': !n.read }"
                      role="button"
                      tabindex="0"
                      @click="handleNotifClick(n)"
                      @keydown.enter="handleNotifClick(n)"
                    >
                      <div class="notif-item__icon" :class="`notif-item__icon--${iconForType(n.type)}`">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" stroke-width="2" stroke-linecap="round"
                             stroke-linejoin="round" v-html="notifIconPath(n.type)"/>
                      </div>
                      <div class="notif-item__body">
                        <p class="notif-item__text">{{ n.message }}</p>
                        <span class="notif-item__time">{{ formatTime(n.createdAt) }}</span>
                      </div>
                      <div class="notif-item__right">
                        <span v-if="!n.read" class="notif-item__dot"></span>
                        <button
                          type="button"
                          class="notif-item__del"
                          title="Dismiss"
                          @click.stop="deleteNotification(n.id)"
                        >×</button>
                      </div>
                    </div>
                  </template>

                  <!-- Yesterday -->
                  <template v-if="grouped.yesterday.length">
                    <p class="notif-group-label">Yesterday</p>
                    <div
                      v-for="n in grouped.yesterday"
                      :key="n.id"
                      class="notif-item"
                      :class="{ 'notif-item--unread': !n.read }"
                      role="button"
                      tabindex="0"
                      @click="handleNotifClick(n)"
                      @keydown.enter="handleNotifClick(n)"
                    >
                      <div class="notif-item__icon" :class="`notif-item__icon--${iconForType(n.type)}`">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" stroke-width="2" stroke-linecap="round"
                             stroke-linejoin="round" v-html="notifIconPath(n.type)"/>
                      </div>
                      <div class="notif-item__body">
                        <p class="notif-item__text">{{ n.message }}</p>
                        <span class="notif-item__time">{{ formatTime(n.createdAt) }}</span>
                      </div>
                      <div class="notif-item__right">
                        <span v-if="!n.read" class="notif-item__dot"></span>
                        <button type="button" class="notif-item__del" title="Dismiss" @click.stop="deleteNotification(n.id)">×</button>
                      </div>
                    </div>
                  </template>

                  <!-- Older -->
                  <template v-if="grouped.older.length">
                    <p class="notif-group-label">Earlier</p>
                    <div
                      v-for="n in grouped.older"
                      :key="n.id"
                      class="notif-item"
                      :class="{ 'notif-item--unread': !n.read }"
                      role="button"
                      tabindex="0"
                      @click="handleNotifClick(n)"
                      @keydown.enter="handleNotifClick(n)"
                    >
                      <div class="notif-item__icon" :class="`notif-item__icon--${iconForType(n.type)}`">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" stroke-width="2" stroke-linecap="round"
                             stroke-linejoin="round" v-html="notifIconPath(n.type)"/>
                      </div>
                      <div class="notif-item__body">
                        <p class="notif-item__text">{{ n.message }}</p>
                        <span class="notif-item__time">{{ formatTime(n.createdAt) }}</span>
                      </div>
                      <div class="notif-item__right">
                        <span v-if="!n.read" class="notif-item__dot"></span>
                        <button type="button" class="notif-item__del" title="Dismiss" @click.stop="deleteNotification(n.id)">×</button>
                      </div>
                    </div>
                  </template>

                  <!-- Empty -->
                  <div v-if="!notifications.length" class="notif-empty">
                    <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                         stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="opacity:0.3">
                      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                      <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
                    </svg>
                    <p>No notifications yet</p>
                    <small>You'll be notified when a language you follow receives a new scenario.</small>
                  </div>
                </div>

              </div>
            </Transition>
          </div>

          <!-- ── Profile dropdown ───────────────────────────────────── -->
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
                <div class="pd-identity">
                  <div class="pd-avatar">{{ initials }}</div>
                  <div class="pd-identity__text">
                    <span class="pd-display-name">{{ currentUser?.displayName || currentUser?.username }}</span>
                    <span class="pd-username">@{{ currentUser?.username }}</span>
                  </div>
                </div>
                <div class="pd-divider"/>
                <div class="pd-section">
                  <RouterLink to="/user" class="pd-item" role="menuitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M20 21a8 8 0 0 0-16 0"/><circle cx="12" cy="8" r="4"/>
                    </svg>
                    My profile
                  </RouterLink>
                  <RouterLink to="/workspace" class="pd-item" role="menuitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/>
                      <rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>
                    </svg>
                    My scenarios
                  </RouterLink>

                  <RouterLink to="/bookmarked-scenarios" class="pd-item" role="menuitem">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
                    </svg>
                    Bookmarked scenarios
                  </RouterLink>
                </div>
                <template v-if="isAdmin">
                  <div class="pd-divider"/>
                  <div class="pd-section">
                    <RouterLink to="/admin" class="pd-item" role="menuitem">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                      </svg>
                      Admin panel
                    </RouterLink>
                  </div>
                </template>
                <div class="pd-divider"/>
                <div class="pd-section">
                  <button type="button" class="pd-item pd-item--danger" role="menuitem" @click="doLogout">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
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

          <RouterLink v-if="!isAuthenticated" to="/login" class="btn btn--ghost">Login</RouterLink>
        </div>
      </div>
    </div>
  </header>

  <Teleport to="body">
    <div v-if="deleteAllConfirm" class="notif-confirm-backdrop" @click.self="cancelDeleteAll">
      <div class="notif-confirm">
        <p class="notif-confirm__eyebrow">Permanent action</p>
        <h2 class="notif-confirm__title">Delete all notifications?</h2>
        <p class="notif-confirm__body">This will permanently remove every notification. This cannot be undone.</p>
        <div class="notif-confirm__actions">
          <button type="button" class="notif-confirm__cancel" @click="cancelDeleteAll">Keep them</button>
          <button type="button" class="notif-confirm__delete" @click="confirmDeleteAllNotifications">Yes, delete all</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* ── Notification trigger ── */
.notif-menu { position: relative; }

.notif-trigger {
  position: relative;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 999px;
  border: 1px solid rgba(255,255,255,0.2);
  background: rgba(255,255,255,0.1);
  color: #fff;
  cursor: pointer;
  transition: background 160ms, border-color 160ms;
}

.notif-trigger:hover,
.notif-trigger.is-open {
  background: rgba(255,255,255,0.18);
  border-color: rgba(255,255,255,0.32);
}

.notif-badge {
  position: absolute;
  top: -3px;
  right: -3px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 999px;
  background: #e53e3e;
  border: 2px solid #1E0812;
  color: #fff;
  font-size: 0.6rem;
  font-weight: 900;
  display: grid;
  place-items: center;
  line-height: 1;
}

/* ── Notification dropdown ── */
.notif-dropdown {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 340px;
  max-height: 480px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 18px;
  box-shadow: 0 8px 24px rgba(30,8,18,0.14), 0 2px 6px rgba(30,8,18,0.06);
  overflow: hidden;
  z-index: 900;
}

.notif-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px 12px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.notif-head__title {
  font-size: 0.92rem;
  font-weight: 800;
  color: var(--text);
  flex: 1;
}

.notif-head__count {
  font-size: 0.72rem;
  font-weight: 800;
  color: #e53e3e;
  background: rgba(229,62,62,0.1);
  border-radius: 999px;
  padding: 2px 8px;
}

.notif-head__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.notif-head__mark-all {
  border: 0;
  background: transparent;
  color: var(--primary);
  font: inherit;
  font-size: 0.76rem;
  font-weight: 700;
  cursor: pointer;
  padding: 0;
  white-space: nowrap;
}
.notif-head__mark-all:hover { text-decoration: underline; }

.notif-head__delete-all {
  border: 0;
  background: transparent;
  color: #e53e3e;
  font: inherit;
  font-size: 0.76rem;
  font-weight: 700;
  cursor: pointer;
  padding: 0;
  white-space: nowrap;
}
.notif-head__delete-all:hover { text-decoration: underline; }

/* ── Notification list ── */
.notif-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
}

.notif-group-label {
  font-size: 0.65rem;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--text-soft);
  padding: 8px 10px 4px;
  margin: 0;
}

.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 120ms;
  position: relative;
}

.notif-item:hover { background: var(--surface-alt); }

.notif-item--unread { background: rgba(192,74,8,0.04); }
.notif-item--unread:hover { background: rgba(192,74,8,0.08); }

.notif-item__icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: #fff;
}

.notif-item__icon--scenario { background: #485B38; }
.notif-item__icon--like     { background: #e53e3e; }
.notif-item__icon--bookmark { background: var(--primary); }
.notif-item__icon--follow   { background: #6d28d9; }
.notif-item__icon--bell,
.notif-item__icon--system   { background: var(--text-soft); }

.notif-item__body { flex: 1; min-width: 0; }

.notif-item__text {
  margin: 0 0 2px;
  font-size: 0.83rem;
  font-weight: 600;
  color: var(--text);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notif-item--unread .notif-item__text { font-weight: 700; }

.notif-item__time {
  font-size: 0.72rem;
  color: var(--text-soft);
}

.notif-item__right {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.notif-item__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
  flex-shrink: 0;
}

.notif-item__del {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-soft);
  font-size: 14px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 120ms, background 120ms;
}

.notif-item:hover .notif-item__del { opacity: 1; }
.notif-item__del:hover { background: var(--border); color: var(--text); }

/* ── Empty state ── */
.notif-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 20px;
  text-align: center;
  color: var(--text-soft);
}

.notif-empty p { margin: 0; font-size: 0.88rem; font-weight: 700; color: var(--text); }
.notif-empty small { font-size: 0.78rem; line-height: 1.5; max-width: 240px; }

/* ── Profile (inchangé) ── */
.profile-menu { position: relative; }

.profile-trigger {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 5px 10px 5px 5px; border-radius: 999px;
  border: 1px solid rgba(255,255,255,0.20);
  background: rgba(255,255,255,0.10);
  color: #fff; cursor: pointer; font: inherit;
  font-weight: 600; font-size: 0.9rem;
  transition: background 160ms ease, border-color 160ms ease;
}
.profile-trigger:hover, .profile-trigger.is-open {
  background: rgba(255,255,255,0.18);
  border-color: rgba(255,255,255,0.32);
}
.profile-trigger__avatar {
  width: 30px; height: 30px; border-radius: 999px;
  background: #F5D4CE; color: #5B1928;
  font-size: 0.72rem; font-weight: 800;
  display: grid; place-items: center; flex-shrink: 0; letter-spacing: 0.04em;
}
.profile-trigger__name { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.profile-trigger__chevron { width: 14px; height: 14px; opacity: 0.7; transition: transform 200ms ease; flex-shrink: 0; }
.profile-trigger.is-open .profile-trigger__chevron { transform: rotate(180deg); }

.profile-dropdown {
  position: absolute; top: calc(100% + 10px); right: 0; width: 230px;
  background: #fff; border: 1px solid var(--border); border-radius: 16px;
  box-shadow: 0 8px 24px rgba(30,8,18,0.14), 0 2px 6px rgba(30,8,18,0.06);
  overflow: hidden; z-index: 900;
}
.pd-identity { display: flex; align-items: center; gap: 10px; padding: 14px 14px 12px; }
.pd-avatar { width: 38px; height: 38px; border-radius: 999px; background: #F5D4CE; color: #5B1928; font-size: 0.78rem; font-weight: 800; display: grid; place-items: center; flex-shrink: 0; letter-spacing: 0.04em; }
.pd-identity__text { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.pd-display-name { font-size: 0.9rem; font-weight: 700; color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pd-username { font-size: 0.76rem; color: var(--text-soft); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pd-divider { height: 1px; background: var(--border); margin: 0; }
.pd-section { padding: 6px; }
.pd-item { display: flex; align-items: center; gap: 9px; width: 100%; padding: 9px 10px; border-radius: 10px; border: 0; background: transparent; color: var(--text); font: inherit; font-size: 0.88rem; font-weight: 600; text-decoration: none; cursor: pointer; transition: background 120ms ease, color 120ms ease; text-align: left; }
.pd-item svg { width: 16px; height: 16px; flex-shrink: 0; color: var(--text-soft); }
.pd-item:hover { background: var(--surface-alt); color: var(--text); }
.pd-item:hover svg { color: var(--primary); }
.pd-item--danger { color: var(--danger); }
.pd-item--danger svg { color: var(--danger); opacity: 0.7; }
.pd-item--danger:hover { background: #fff1f2; color: var(--danger); }

.pd-enter-active { transition: opacity 160ms ease, transform 160ms ease; }
.pd-leave-active { transition: opacity 120ms ease, transform 120ms ease; }
.pd-enter-from   { opacity: 0; transform: translateY(-6px) scale(0.97); }
.pd-leave-to     { opacity: 0; transform: translateY(-4px) scale(0.97); }

/* ── Delete-all confirmation ── */
.notif-confirm-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(30, 8, 18, 0.55);
  backdrop-filter: blur(4px);
}
.notif-confirm {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: min(420px, 100%);
  border: 3px solid #1E0812;
  border-radius: 18px;
  padding: 26px;
  background: #FFF0EE;
  box-shadow: 6px 6px 0 #1E0812;
}
.notif-confirm__eyebrow { margin: 0; font-size: 0.65rem; font-weight: 900; color: #A8334C; letter-spacing: 0.14em; text-transform: uppercase; }
.notif-confirm__title { margin: 0; font-size: 1.25rem; font-weight: 950; color: #1E0812; letter-spacing: -0.02em; }
.notif-confirm__body { margin: 0; font-size: 0.88rem; color: #785068; line-height: 1.55; }
.notif-confirm__actions { display: flex; gap: 10px; }
.notif-confirm__cancel { flex: 1; border: 1.5px solid #D4E5CA; border-radius: 12px; padding: 12px; background: transparent; color: #785068; font: inherit; font-weight: 700; cursor: pointer; transition: background 140ms ease; }
.notif-confirm__cancel:hover { background: #D4E5CA; }
.notif-confirm__delete { flex: 1; border: 0; border-radius: 12px; padding: 12px; background: #A8334C; color: #fff; font: inherit; font-size: 0.92rem; font-weight: 800; cursor: pointer; transition: background 160ms ease; }
.notif-confirm__delete:hover { background: #8b2940; }
</style>
