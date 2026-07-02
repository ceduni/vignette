<script setup>
import {RouterLink} from "vue-router";
import GlobePolaroids from "../components/GlobePolaroids.vue";

const MOCK_SCENARIOS = [
  {
    id: 1,
    title: "Greetings & daily life in Wolof",
    author: "amina.diallo",
    lang: "wol",
    gradient: "linear-gradient(135deg, #A8C498 0%, #485B38 100%)",
    scenes: 6,
  },
  {
    id: 2,
    title: "Market interactions in Hausa",
    author: "ibrahim.k",
    lang: "hau",
    gradient: "linear-gradient(135deg, #F5D4CE 0%, #785068 100%)",
    scenes: 4,
  },
  {
    id: 3,
    title: "Family vocabulary in Quechua",
    author: "rosa.quispe",
    lang: "que",
    gradient: "linear-gradient(135deg, #DFE8DA 0%, #4A7C6F 100%)",
    scenes: 8,
  },
  {
    id: 4,
    title: "Yoruba oral storytelling tradition",
    author: "lekan.adeyemi",
    lang: "yor",
    gradient: "linear-gradient(135deg, #E5D0CC 0%, #6B5B35 100%)",
    scenes: 5,
  },
];
</script>

<template>
  <main class="page home-page">

    <!-- ── Hero ─────────────────────────────────────── -->
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

    <!-- ── Community strip ──────────────────────────── -->
    <section class="home-community">
      <div class="home-community__head">
        <div class="home-community__label">
          <span class="home-pulse home-pulse--sm"></span>
          From the community
        </div>
        <RouterLink to="/scenarios" class="btn btn--ghost home-community__btn">
          Browse all scenarios
          <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2"
               width="13" height="13" aria-hidden="true">
            <path d="M4 10h12M10 4l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </RouterLink>
      </div>

      <div class="home-feed">
        <div
            v-for="s in MOCK_SCENARIOS"
            :key="s.id"
            class="home-card"
        >
          <div class="home-card__thumb" :style="{background: s.gradient}">
            <span class="home-card__scene-count">{{ s.scenes }} scenes</span>
          </div>
          <div class="home-card__body">
            <strong class="home-card__name">{{ s.title }}</strong>
            <span class="home-card__meta">
              {{ s.author }}
              <span class="home-card__lang">{{ s.lang }}</span>
            </span>
          </div>
        </div>
      </div>
    </section>

  </main>
</template>
