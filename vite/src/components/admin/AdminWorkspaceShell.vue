<script setup>
import {computed} from "vue";
import {RouterLink, useRoute} from "vue-router";

const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  subtitle: {
    type: String,
    default: "",
  },
});

const route = useRoute();

const items = [
  {to: "/admin", label: "Dashboard"},
  {to: "/admin/users", label: "Users"},
  {to: "/admin/languages", label: "Languages"},
  {to: "/admin/scenarios", label: "Scenarios"},
  {to: "/admin/community", label: "Community"},
];

const currentPath = computed(() => route.path);

function isActive(path) {
  return currentPath.value === path;
}
</script>

<template>
  <main class="admin-shell-page">
    <section class="section admin-workspace">
      <aside class="admin-sidebar">
        <div class="admin-sidebar__inner">
          <p class="admin-sidebar__eyebrow">Administration</p>
          <nav class="admin-nav" aria-label="Admin navigation">
            <RouterLink
                v-for="item in items"
                :key="item.to"
                :to="item.to"
                class="admin-nav__link"
                :class="{ 'is-active': isActive(item.to) }"
            >
              {{ item.label }}
            </RouterLink>
          </nav>
        </div>
      </aside>

      <div class="admin-main">
        <header class="admin-header">
          <div>
            <p class="admin-header__eyebrow">Administration</p>
            <h1>{{ title }}</h1>
            <p v-if="subtitle" class="muted admin-header__subtitle">{{ subtitle }}</p>
          </div>
        </header>

        <slot />
      </div>
    </section>
  </main>
</template>

<style scoped>
.admin-workspace {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 1.5rem;
  align-items: start;
  padding: 1.5rem;
}

.admin-sidebar {
  position: sticky;
  top: 1rem;
}

.admin-sidebar__inner,
.admin-header {
  border: 1px solid var(--border);
  border-radius: 20px;
}

.admin-sidebar__inner {
  padding: 1rem 0.85rem;
  background: var(--primary);
  color: #fff;
}

.admin-sidebar__eyebrow,
.admin-header__eyebrow {
  margin: 0 0 0.6rem;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  font-size: 0.75rem;
  font-weight: 700;
}

.admin-sidebar__eyebrow {
  color: rgba(255, 253, 251, 0.72);
}

.admin-header__eyebrow {
  color: var(--text-soft);
}

.admin-nav {
  display: grid;
  gap: 0.35rem;
}

.admin-nav__link {
  display: flex;
  align-items: center;
  min-height: 2.5rem;
  padding: 0.7rem 0.85rem;
  border-radius: 12px;
  color: rgba(255, 253, 251, 0.78);
  text-decoration: none;
  transition: background 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.admin-nav__link:hover {
  background: rgba(255, 253, 251, 0.12);
  color: #fff;
}

.admin-nav__link.is-active {
  background: rgba(255, 253, 251, 0.18);
  color: #fff;
}

.admin-main {
  display: grid;
  gap: 1rem;
  min-width: 0;
}

.admin-header {
  padding: 1rem 1.15rem;
  background: var(--surface);
}

.admin-header h1 {
  margin: 0;
}

.admin-header__subtitle {
  margin: 0.35rem 0 0;
}

@media (max-width: 960px) {
  .admin-workspace {
    grid-template-columns: 1fr;
  }

  .admin-sidebar {
    position: static;
  }

  .admin-nav {
    grid-template-columns: repeat(5, minmax(120px, 1fr));
    overflow-x: auto;
  }

  .admin-nav__link {
    white-space: nowrap;
    justify-content: center;
  }
}

@media (max-width: 640px) {
  .admin-nav {
    grid-template-columns: repeat(5, max-content);
  }
}
</style>
