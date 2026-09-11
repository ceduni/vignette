<script setup>
import {ref, watch} from 'vue';
import {updateScenarioMetadata} from '../../api/scenarios';

const props = defineProps({scenarioId: [String, Number], location: Object, sandbox: Boolean});
const emit = defineEmits(['saved']);
const name = ref('');
const latitude = ref('');
const longitude = ref('');
const saving = ref(false);
const error = ref('');
watch(() => props.location, location => {
  name.value = location?.name ?? '';
  latitude.value = location?.latitude ?? '';
  longitude.value = location?.longitude ?? '';
}, {immediate: true});

async function save(clear = false) {
  error.value = '';
  saving.value = true;
  const location = clear ? {} : {name: name.value.trim(), latitude: Number(latitude.value), longitude: Number(longitude.value)};
  try {
    const saved = props.sandbox ? {location: clear ? null : location}
        : await updateScenarioMetadata(props.scenarioId, {location});
    emit('saved', saved.location);
  } catch (e) {
    error.value = e.message || 'Could not save the story location.';
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <form class="story-location" @submit.prevent="save()">
    <p>Choose where this story takes place. This is where it appears on the globe.</p>
    <label>Place <input v-model="name" maxlength="200" placeholder="Dakar, Senegal" :disabled="saving"/></label>
    <div class="story-location__coordinates">
      <label>Latitude <input v-model="latitude" type="number" min="-90" max="90" step="any" required :disabled="saving"/></label>
      <label>Longitude <input v-model="longitude" type="number" min="-180" max="180" step="any" required :disabled="saving"/></label>
    </div>
    <div class="story-location__actions">
      <button type="submit" :disabled="saving">{{ saving ? 'Saving…' : 'Save location' }}</button>
      <button v-if="location" type="button" :disabled="saving" @click="save(true)">Remove location</button>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
  </form>
</template>

<style scoped>
.story-location { display: grid; gap: 10px; }
.story-location p { margin: 0; font-size: 0.85rem; }
.story-location__coordinates { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.story-location__coordinates label { min-width: 0; }
.story-location__actions { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
