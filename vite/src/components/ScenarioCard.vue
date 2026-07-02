<script setup>
import {computed} from "vue";
import {RouterLink} from "vue-router";
import BaseBadge from "./ui/BaseBadge.vue";

const props = defineProps({
  scenario: {type: Object, required: true},
  previewId: {type: [String, Number, null], default: null},
});

const description = computed(() => {
  const raw = props.scenario.description?.trim() || "No description available.";
  if (raw.length <= 150) return raw;
  return raw.slice(0, 147) + "...";
});

const statusVariant = computed(() => {
  return props.scenario.visibilityStatus === "PUBLISHED" ? "success" : "warning";
});
</script>

<template>
  <article class="card scenario-card">
    <div class="scenario-card__media">
      <img
          v-if="previewId"
          :src="`/api/thumbnails/${previewId}/content`"
          :alt="scenario.title ?? 'Scenario preview'"
          class="scenario-card__image"
      />
      <div v-else class="scenario-card__placeholder">
        No preview image yet
      </div>
    </div>

    <div class="scenario-card__body">
      <div class="scenario-card__top">
        <h3 class="scenario-card__title">
          {{ scenario.title ?? "Untitled scenario" }}
        </h3>

        <div class="scenario-card__badges">
          <BaseBadge variant="info">
            {{ scenario.languageId ?? "Unknown language" }}
          </BaseBadge>
          <BaseBadge variant="neutral">
            {{ scenario.authorUsername ?? "Unknown author" }}
          </BaseBadge>
          <BaseBadge :variant="statusVariant">
            {{ scenario.visibilityStatus ?? "UNKNOWN" }}
          </BaseBadge>
        </div>
        <div v-if="scenario.tags?.length" class="scenario-card__tags">
          <BaseBadge
              v-for="tag in scenario.tags"
              :key="tag"
              variant="neutral"
          >
            #{{ tag }}
          </BaseBadge>
        </div>
      </div>

      <p class="scenario-card__description">
        {{ description }}
      </p>

      <div class="scenario-card__footer">
        <RouterLink :to="`/scenarios/${scenario.id}`" class="btn btn--primary">
          Open storyboard
        </RouterLink>
      </div>
    </div>
  </article>
</template>