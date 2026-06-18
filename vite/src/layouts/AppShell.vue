<template>
  <div class="app-shell">
    <AppHeader/>
    <BaseToast/>

    <div class="app-shell__body">
      <slot/>
    </div>
  </div>
</template>

<script setup>
import AppHeader from "../components/AppHeader.vue";
import BaseToast from "../components/ui/BaseToast.vue";
</script>
