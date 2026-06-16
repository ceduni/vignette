<script setup>
import {RouterLink} from "vue-router";
import GlobePolaroids from "../components/GlobePolaroids.vue";
</script>

<template>
  <main class="page">
    <section class="hero hero--enhanced">
      <div class="hero__content">
        <span class="eyebrow">Research and community platform</span>
        <h1>Build richer language documentation workflows from scenes, audio and collaborative analysis</h1>
        <p class="hero__text">
          Vignette helps create multimedia elicitation scenarios for language documentation,
          especially when simple translation prompts are not enough. It combines visual prompts,
          oral recordings, discussions and collaborative curation in one place.
        </p>

        <div class="hero__actions">
          <RouterLink to="/scenarios" class="btn btn--primary">
            Browse scenarios
          </RouterLink>
          <RouterLink to="/languages" class="btn btn--ghost">
            Explore languages
          </RouterLink>
          <RouterLink to="/about" class="btn btn--ghost">
            Learn about the project
          </RouterLink>
        </div>
      </div>

      <div class="hero-globe">
        <GlobePolaroids />
        <p class="globe-hint">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
               stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 12c0-4.4 3.6-8 8-8s8 3.6 8 8"/>
            <polyline points="18 8 22 8 22 4"/>
          </svg>
          Drag to spin
        </p>
      </div>

    </section>

  </main>
</template>