<script setup>
import {nextTick, onBeforeUnmount, onMounted, ref} from "vue";
import {RouterLink} from "vue-router";
import GlobePolaroids from "../components/GlobePolaroids.vue";
import {fetchScenarios, fetchScenarioThumbnails} from "../api/scenarios";
import {buildApiUrl} from "../api/rest";

const COMMUNITY_LIMIT = 10;
const communityScenarios = ref([]);
const communityLoading = ref(true);
const communityError = ref("");
const brokenPreviewIds = ref(new Set());
const communityFeed = ref(null);
const canScrollBack = ref(false);
const canScrollForward = ref(false);

const FALLBACK_GRADIENTS = [
  "linear-gradient(135deg, #A8C498 0%, #485B38 100%)",
  "linear-gradient(135deg, #F5D4CE 0%, #785068 100%)",
  "linear-gradient(135deg, #DFE8DA 0%, #4A7C6F 100%)",
  "linear-gradient(135deg, #E5D0CC 0%, #6B5B35 100%)",
];

function asList(value) {
  return Array.isArray(value) ? value : (value?.content ?? []);
}

function previewUrl(scenario) {
  if (!scenario?.previewId || brokenPreviewIds.value.has(scenario.previewId)) return "";
  return buildApiUrl(`/api/thumbnails/${scenario.previewId}/content`);
}

function fallbackGradient(index) {
  return FALLBACK_GRADIENTS[index % FALLBACK_GRADIENTS.length];
}

function markPreviewBroken(previewId) {
  brokenPreviewIds.value = new Set([...brokenPreviewIds.value, previewId]);
}

function updateCommunityScrollState() {
  const feed = communityFeed.value;
  if (!feed) {
    canScrollBack.value = false;
    canScrollForward.value = false;
    return;
  }

  canScrollBack.value = feed.scrollLeft > 4;
  canScrollForward.value = feed.scrollLeft + feed.clientWidth < feed.scrollWidth - 4;
}

function scrollCommunity(direction) {
  const feed = communityFeed.value;
  if (!feed) return;

  feed.scrollBy({
    left: direction * Math.max(260, feed.clientWidth * 0.82),
    behavior: "smooth",
  });
}

async function loadCommunityScenarios() {
  communityLoading.value = true;
  communityError.value = "";

  try {
    const scenarioResponse = await fetchScenarios();
    const published = asList(scenarioResponse)
        .filter((scenario) => scenario.visibilityStatus === "PUBLISHED")
        .slice(0, COMMUNITY_LIMIT);

    communityScenarios.value = await Promise.all(
        published.map(async (scenario) => {
          try {
            const thumbnails = asList(await fetchScenarioThumbnails(scenario.id))
                .sort((a, b) => (a.idx ?? 0) - (b.idx ?? 0));
            return {
              ...scenario,
              previewId: thumbnails[0]?.id ?? null,
              sceneCount: thumbnails.length,
            };
          } catch {
            return {...scenario, previewId: null, sceneCount: 0};
          }
        })
    );
    await nextTick();
    updateCommunityScrollState();
  } catch (error) {
    communityScenarios.value = [];
    communityError.value = error?.message || "Community scenarios are unavailable right now.";
  } finally {
    communityLoading.value = false;
  }
}

onMounted(() => {
  window.addEventListener("resize", updateCommunityScrollState, {passive: true});
  loadCommunityScenarios();
});
onBeforeUnmount(() => window.removeEventListener("resize", updateCommunityScrollState));
</script>

<template>
  <main class="page home-page">

    <section class="home-hero">

      <div class="home-hero__left">
        <div class="home-eyebrow">
          <span class="home-pulse"></span>
          Language documentation platform
        </div>

        <h1 class="home-title">
          Build richer<br>language stories
        </h1>

        <p class="home-sub">
          Visual scenes, oral recordings and collaborative analysis.
          From fieldwork to community.
        </p>

        <div class="home-actions">
          <RouterLink to="/scenarios" class="btn btn--primary home-btn">
            Browse scenarios
          </RouterLink>
          <RouterLink to="/languages" class="btn btn--ghost home-btn">
            Explore languages
          </RouterLink>
        </div>
      </div>

      <div class="home-hero__right">
        <div class="home-globe-glow"></div>
        <GlobePolaroids :speed="0.004" />
        <p class="home-drag-hint">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round" width="15" height="15" aria-hidden="true">
            <path d="M4 12c0-4.4 3.6-8 8-8s8 3.6 8 8"/>
            <polyline points="18 8 22 8 22 4"/>
          </svg>
          Drag to spin
        </p>
      </div>

    </section>

    <section class="home-community">
      <div class="home-community__head">
        <div class="home-community__label">
          <span class="home-pulse home-pulse--sm"></span>
          From the community
        </div>
        <div class="home-community__actions">
          <div v-if="communityScenarios.length > 1" class="home-community__nav" aria-label="Scroll community scenarios">
            <button
                type="button"
                class="home-community__arrow"
                aria-label="Show previous scenarios"
                :disabled="!canScrollBack"
                @click="scrollCommunity(-1)"
            >
              <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M16 10H4M10 4l-6 6 6 6" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
            <button
                type="button"
                class="home-community__arrow"
                aria-label="Show more scenarios"
                :disabled="!canScrollForward"
                @click="scrollCommunity(1)"
            >
              <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                <path d="M4 10h12M10 4l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
          </div>
          <RouterLink to="/scenarios" class="btn btn--ghost home-community__btn">
            Browse all scenarios
            <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"
                 width="13" height="13" aria-hidden="true">
              <path d="M4 10h12M10 4l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </RouterLink>
        </div>
      </div>

      <div v-if="communityLoading" class="home-feed" aria-label="Loading community scenarios">
        <div v-for="index in 4" :key="index" class="home-card home-card--skel" aria-hidden="true">
          <div class="home-card__thumb"></div>
          <div class="home-card__body">
            <span class="skel--line skel--w70"></span>
            <span class="skel--line skel--w40"></span>
          </div>
        </div>
      </div>

      <div
          v-else-if="communityScenarios.length"
          ref="communityFeed"
          class="home-feed home-feed--scroll"
          tabindex="0"
          aria-label="Published community scenarios. Scroll horizontally to see more."
          @scroll.passive="updateCommunityScrollState"
      >
        <RouterLink
            v-for="(scenario, index) in communityScenarios"
            :key="scenario.id"
            :to="`/scenarios/${scenario.id}`"
            class="home-card"
            :aria-label="`Open ${scenario.title}`"
        >
          <div class="home-card__thumb" :style="{background: fallbackGradient(index)}">
            <img
                v-if="previewUrl(scenario)"
                :src="previewUrl(scenario)"
                :alt="`${scenario.title} scene preview`"
                loading="lazy"
                @error="markPreviewBroken(scenario.previewId)"
            />
            <div v-else class="home-card__fallback" aria-hidden="true">
              {{ scenario.title?.charAt(0) || "V" }}
            </div>
            <span class="home-card__scene-count">
              {{ scenario.sceneCount }} {{ scenario.sceneCount === 1 ? "scene" : "scenes" }}
            </span>
          </div>
          <div class="home-card__body">
            <strong class="home-card__name">{{ scenario.title }}</strong>
            <span class="home-card__meta">
              {{ scenario.authorUsername || "Vignette community" }}
              <span class="home-card__lang">{{ scenario.languageId }}</span>
            </span>
          </div>
        </RouterLink>
      </div>

      <div v-else class="home-community__state" role="status">
        <strong>{{ communityError ? "Couldn’t load community stories" : "No published scenarios yet" }}</strong>
        <span>
          {{ communityError || "Be the first to share a visual language story with the community." }}
        </span>
        <RouterLink to="/scenarios" class="home-community__state-link">
          Open the scenario catalogue →
        </RouterLink>
      </div>
    </section>

  </main>
</template>
