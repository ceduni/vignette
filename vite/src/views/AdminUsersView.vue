<script setup>
import {computed, onMounted, ref} from "vue";
import {fetchAdminUsers, updateAdminUserRoles} from "../api/admin";

const loading = ref(false);
const savingUserId = ref(null);
const error = ref("");
const success = ref("");
const users = ref([]);
const query = ref("");

const visibleRoles = [
  {
    value: "ROLE_USER",
    label: "User",
    description: "Standard authenticated account",
    locked: false,
  },
  {
    value: "ROLE_LINGUIST",
    label: "Linguist",
    description: "Specialized linguistic contributor",
    locked: false,
  },
  {
    value: "ROLE_ADMIN",
    label: "Admin",
    description: "Global administration privileges",
    locked: true,
  },
];

const filteredUsers = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return users.value;

  return users.value.filter((user) => {
    return [
      user.username,
      user.displayName,
      user.email,
      ...(user.roles ?? []),
    ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(q));
  });
});

function normalizeDraftRoles(user) {
  const existing = new Set(user.roles ?? []);
  if (existing.size === 0) {
    existing.add("ROLE_USER");
  }
  return [...existing];
}

async function loadUsers() {
  loading.value = true;
  error.value = "";
  success.value = "";

  try {
    const rows = await fetchAdminUsers();
    users.value = rows.map((user) => ({
      ...user,
      draftRoles: normalizeDraftRoles(user),
    }));
  } catch (e) {
    error.value = e.message || "Failed to load users.";
  } finally {
    loading.value = false;
  }
}

function roleMeta(role) {
  return visibleRoles.find((item) => item.value === role) ?? {
    value: role,
    label: role.replace(/^ROLE_/, ""),
    description: "",
    locked: false,
  };
}

function hasDraftRole(user, role) {
  return (user.draftRoles ?? []).includes(role);
}

function isRoleLocked(role) {
  return !!roleMeta(role)?.locked;
}

function toggleRole(user, role) {
  if (isRoleLocked(role)) return;

  const current = new Set(user.draftRoles ?? []);
  if (current.has(role)) {
    current.delete(role);
  } else {
    current.add(role);
  }

  if (![...current].some((value) => value !== "ROLE_ADMIN")) {
    current.add("ROLE_USER");
  }

  user.draftRoles = [...current];
}

function sanitizedRolesForSave(user) {
  return (user.draftRoles ?? []).filter((role) => !isRoleLocked(role));
}

function effectiveComparableRoles(user) {
  return [...(user.roles ?? [])]
      .filter((role) => !isRoleLocked(role))
      .sort()
      .join("|");
}

function effectiveComparableDraftRoles(user) {
  return [...sanitizedRolesForSave(user)]
      .sort()
      .join("|");
}

function rolesChanged(user) {
  return effectiveComparableRoles(user) !== effectiveComparableDraftRoles(user);
}

async function saveRoles(user) {
  savingUserId.value = user.id;
  error.value = "";
  success.value = "";

  try {
    const updated = await updateAdminUserRoles(user.id, sanitizedRolesForSave(user));

    const lockedRolesStillPresent = (user.roles ?? []).filter((role) => isRoleLocked(role));
    const mergedRoles = [...new Set([...(updated.roles ?? []), ...lockedRolesStillPresent])];

    const index = users.value.findIndex((u) => u.id === user.id);
    if (index >= 0) {
      users.value[index] = {
        ...updated,
        roles: mergedRoles,
        draftRoles: [...mergedRoles],
      };
    }

    success.value = `Roles updated for ${updated.username}.`;
  } catch (e) {
    error.value = e.message || "Failed to update user roles.";
  } finally {
    savingUserId.value = null;
  }
}

onMounted(loadUsers);
</script>

<template>
  <main class="page">
    <section class="section">
      <div class="section-heading">
        <div>
          <h1>Admin users</h1>
          <p class="muted">
            Review accounts and update editable roles. Sensitive roles remain visible but locked.
          </p>
        </div>

        <div class="toolbar admin-toolbar">
          <input v-model="query" placeholder="Search users..."/>
          <button class="btn btn--ghost" @click="loadUsers">
            Refresh
          </button>
        </div>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="success" class="success">{{ success }}</p>

      <div v-if="loading" class="loader-block">
        <span class="loader-spinner"></span>
        <span class="muted">Loading users...</span>
      </div>

      <div v-else-if="filteredUsers.length" class="admin-users-list">
        <article v-for="user in filteredUsers" :key="user.id" class="card admin-user-card">
          <div class="admin-user-card__top">
            <div>
              <h2>{{ user.displayName || user.username }}</h2>
              <p class="muted">
                #{{ user.id }} · {{ user.username }} · {{ user.email }}
              </p>
            </div>

            <div class="admin-user-card__badges">
              <span class="badge">
                {{ user.profilePublic ? "Public profile" : "Private profile" }}
              </span>
              <span v-if="(user.roles ?? []).includes('ROLE_ADMIN')" class="badge badge--accent">
                Admin account
              </span>
            </div>
          </div>

          <div class="admin-user-card__roles">
            <button
                v-for="role in visibleRoles"
                :key="role.value"
                type="button"
                class="role-pill"
                :class="{
                  'is-active': hasDraftRole(user, role.value),
                  'is-locked': role.locked,
                }"
                :disabled="role.locked"
                @click="toggleRole(user, role.value)"
            >
              <span class="role-pill__label">{{ role.label }}</span>
              <span class="role-pill__meta">
                {{ role.locked ? "Locked" : (hasDraftRole(user, role.value) ? "Enabled" : "Disabled") }}
              </span>
            </button>
          </div>

          <div class="admin-user-card__legend">
            <p class="muted">
              <strong>Note:</strong> the Admin role is visible for transparency but cannot be edited from this page.
            </p>
          </div>

          <div class="toolbar">
            <button
                class="btn btn--primary"
                :disabled="savingUserId === user.id || !rolesChanged(user)"
                @click="saveRoles(user)"
            >
              {{ savingUserId === user.id ? "Saving..." : "Save roles" }}
            </button>
          </div>
        </article>
      </div>

      <div v-else class="empty-state">
        <h3>No user found</h3>
        <p class="muted">Try another search term.</p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.admin-toolbar {
  align-items: center;
}

.admin-users-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-user-card {
  border-radius: 22px;
  padding: 24px 26px;
  background: linear-gradient(180deg, #FFFCF7 0%, #FFF7EF 100%);
  box-shadow: var(--shadow);
}

.admin-user-card__top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.admin-user-card__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.badge {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 0.35rem 0.8rem;
  background: var(--surface-alt);
  font-size: 0.82rem;
  font-weight: 600;
}

.badge--accent {
  background: var(--accent-warm);
  border-color: rgba(192, 74, 8, 0.18);
}

.admin-user-card__roles {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 18px;
}

.role-pill {
  appearance: none;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: #FFFCF7;
  color: var(--text);
  padding: 0.8rem 1rem;
  min-width: 180px;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: 160ms ease;
  box-shadow: 0 6px 16px rgba(42, 21, 0, 0.04);
}

.role-pill:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(192, 74, 8, 0.28);
  background: #fff7ef;
}

.role-pill.is-active {
  background: linear-gradient(180deg, #fff3e8 0%, #ffe8d0 100%);
  border-color: rgba(192, 74, 8, 0.35);
}

.role-pill.is-locked {
  background: #f3f4f6;
  border-color: #d4d8de;
  color: var(--text-soft);
  cursor: not-allowed;
  opacity: 0.88;
}

.role-pill__label {
  font-weight: 700;
  font-size: 0.98rem;
}

.role-pill__meta {
  font-size: 0.78rem;
  color: var(--text-soft);
}

.admin-user-card__legend {
  margin-top: 14px;
}

@media (max-width: 720px) {
  .admin-user-card {
    padding: 18px;
  }

  .admin-user-card__top {
    flex-direction: column;
    align-items: flex-start;
  }

  .role-pill {
    width: 100%;
    min-width: 0;
  }
}
</style>