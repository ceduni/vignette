<script setup>
import AppShell from "./layouts/AppShell.vue";
import {RouterView} from "vue-router";

function routeViewKey(route) {
  const params = Object.entries(route.params || {})
      .map(([key, value]) => `${key}:${Array.isArray(value) ? value.join(",") : value}`)
      .join("|");
  return `${String(route.name || route.path)}:${params}`;
}
</script>

<template>
  <AppShell>
    <RouterView v-slot="{ Component, route }">
      <component :is="Component" :key="routeViewKey(route)"/>
    </RouterView>
  </AppShell>
</template>
