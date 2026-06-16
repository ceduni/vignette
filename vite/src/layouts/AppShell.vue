<template>
  <div class="app-shell">
    <AppHeader/>
    <BaseToast/>

    <div class="app-shell__body">
      <slot/>
    </div>

    <AppFooter v-if="!hideFooter"/>
  </div>
</template>

<script setup>
import {computed} from "vue";
import {useRoute} from "vue-router";
import AppHeader from "../components/AppHeader.vue";
import AppFooter from "../components/AppFooter.vue";
import BaseToast from "../components/ui/BaseToast.vue";

const route = useRoute();
const hideFooter = computed(() => route.meta?.hideFooter === true);
</script>
