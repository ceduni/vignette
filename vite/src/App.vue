<script setup>
import AppShell from "./layouts/AppShell.vue";
import {RouterView} from "vue-router";
</script>

<template>
  <AppShell>
    <RouterView v-slot="{ Component, route }">
      <component :is="Component" :key="route.fullPath"/>
    </RouterView>
  </AppShell>
</template>
