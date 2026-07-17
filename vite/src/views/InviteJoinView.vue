<script setup>
import {onMounted, ref} from "vue";
import {useRouter} from "vue-router";
import {joinViaInviteLink} from "../api/collaborators";
import {useToast} from "../composables/useToast";
import BaseLoader from "../components/ui/BaseLoader.vue";
import BaseAlert from "../components/ui/BaseAlert.vue";

const props = defineProps({
  token: {type: String, required: true},
});

const router = useRouter();
const toast = useToast();
const error = ref("");

onMounted(async () => {
  try {
    const collaborator = await joinViaInviteLink(props.token);
    toast.success("You've joined this scenario.");
    await router.replace(`/scenarios/${collaborator.scenarioId}`);
  } catch (e) {
    error.value = e.message || "This invite link is invalid or has expired.";
  }
});
</script>

<template>
  <main class="page">
    <BaseLoader v-if="!error">Joining scenario...</BaseLoader>
    <BaseAlert v-else type="error">{{ error }}</BaseAlert>
  </main>
</template>