<template>
  <div class="app-shell">
    <AppHeader/>
    <BaseToast/>

    <div class="app-shell__body">
      <slot/>
    </div>

    <EmergencyAudioRecorder v-if="!isStudio"/>
    <AppFooter v-if="!hideFooter"/>
  </div>
</template>

<script setup>
import {computed} from "vue";
import {useRoute} from "vue-router";
import AppHeader from "../components/AppHeader.vue";
import AppFooter from "../components/AppFooter.vue";
import BaseToast from "../components/ui/BaseToast.vue";
import EmergencyAudioRecorder from "../components/EmergencyAudioRecorder.vue";

const route = useRoute();
const isStudio = computed(() => route.name === "scenario-detail");
const isAdminRoute = computed(() => {
  const path = String(route.path || "");
  const name = String(route.name || "");
  return path.startsWith("/admin") || name.startsWith("admin");
});
const hideFooter = computed(
    () =>
        route.name === "languages" ||
        route.name === "languages-map" ||
        isAdminRoute.value
);
</script>