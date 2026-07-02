<template>
  <div class="app-shell">
    <AppHeader/>
    <BaseToast/>

    <div class="app-shell__body">
      <slot/>
    </div>

    <EmergencyAudioRecorder v-if="showEmergencyRecorder"/>
    <AppFooter/>
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
const workflowRoutes = new Set(["workspace", "create-scenario", "user", "scenario-manage"]);
const isStudio = computed(() => route.name === "scenario-detail");
const showEmergencyRecorder = computed(() => !isStudio.value && workflowRoutes.has(route.name));
</script>
