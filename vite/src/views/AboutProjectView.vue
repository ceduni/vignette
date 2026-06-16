<template>
  <main class="page">
    <section class="section">

      <div class="section-heading">
        <div>
          <h1>About Vignette</h1>
          <p class="muted">Origins, purpose, design logic and intended use.</p>
        </div>
      </div>

      <!-- Quick overview -->
      <div class="card-grid">
        <article class="card about-card">
          <span class="about-card__label">Why it exists</span>
          <p class="text">
            Many documentation workflows rely too heavily on translation.
            Vignette pushes toward scene-based, contextual, multimodal elicitation
            so that language use is captured in richer, more situated ways.
          </p>
        </article>

        <article class="card about-card">
          <span class="about-card__label">What it supports</span>
          <p class="text">
            Scenarios built from ordered image sequences, audio collected against
            individual thumbnails and markers, language-centred metadata, and
            collaborative discussion around every layer of the documentation.
          </p>
        </article>

        <article class="card about-card">
          <span class="about-card__label">Who it is for</span>
          <p class="text">
            Researchers, students, documentation teams and language communities
            working with low-resource languages where richer, scene-grounded
            elicitation material makes a real difference.
          </p>
        </article>
      </div>

      <!-- Origin -->
      <section class="card">
        <h2>Origin of the project</h2>
        <p class="text">
          Vignette was designed around a simple problem: language documentation often becomes too dependent
          on direct translation tasks and isolated lexical elicitation. That is useful, but it is also limited.
        </p>
        <p class="text">
          In many cases, researchers and communities need richer prompts grounded in scenes, actions,
          participants and context. The project therefore focuses on multimedia vignettes as a basis for
          elicitation, annotation and discussion.
        </p>
      </section>

      <!-- Main goal -->
      <section class="card">
        <h2>Main goal</h2>
        <p class="text">
          The goal is to support a workflow where images, ordered thumbnails, markers and oral responses
          make it easier to document language use in a more situated and less translation-driven way.
        </p>
        <p class="text">
          Instead of reducing everything to word-for-word equivalences across languages, Vignette aims to help
          document how speakers interpret situations, describe scenes, narrate events and negotiate meaning.
        </p>
      </section>

      <!-- Why it matters -->
      <div class="card-grid">
        <article class="card">
          <h3>Why multimedia matters</h3>
          <p class="text">
            Visual prompts and audio can preserve context, discourse structure, pronunciation and speaker choices
            better than plain text forms alone.
          </p>
        </article>

        <article class="card">
          <h3>Why collaboration matters</h3>
          <p class="text">
            Documentation work is rarely solitary. Vignette includes discussion spaces so interpretation,
            glossing and comments can accumulate around the data.
          </p>
        </article>

        <article class="card">
          <h3>Why low-resource languages matter</h3>
          <p class="text">
            The platform is especially relevant when digital tools are scarce and documentation material must be
            created, organized and discussed carefully.
          </p>
        </article>
      </div>

      <!-- Platform support -->
      <section class="card">
        <h2>What the platform tries to support</h2>
        <p class="text">
          Scenario creation from ordered image sequences lets researchers compose visual prompts that
          mirror real situations rather than isolated words or phrases.
        </p>
        <p class="text">
          Audio collection is tied to individual thumbnails and markers, so responses are always anchored
          to the exact moment or scene that prompted them.
        </p>
        <p class="text">
          Language-centred metadata and navigation means everything stays organized around the language
          being documented, not around researcher-imposed categories.
        </p>
        <p class="text">
          Collaborative discussion around language entries and scenarios allows glossing, translation notes
          and interpretive comments to build up alongside the primary material.
        </p>
        <p class="text">
          Progressive publication rather than rigid one-shot workflows lets projects grow naturally
          from early drafts to shareable, citable resources over time.
        </p>
      </section>

      <!-- Philosophy -->
      <section class="card">
        <h2>Practical philosophy</h2>
        <p class="text">
          Vignette is not meant to replace every existing documentation method. It is meant to fill a gap:
          giving a cleaner home to scenario-based elicitation and the collaborative material that comes with it.
        </p>
        <p class="text">
          The long-term value is not just storing files. It is structuring reusable documentation objects that
          stay interpretable by other people later.
        </p>
      </section>

      <!-- How it works -->
      <div class="section-heading">
        <div>
          <h2>How it works</h2>
          <p class="muted">A workflow built for documentation, elicitation, and collaboration.</p>
        </div>
      </div>

      <div class="card-grid">
        <article class="card">
          <h3>Explore language entries</h3>
          <p class="text">
            Start from existing language entries, metadata, genealogy and linked scenarios.
          </p>
        </article>

        <article class="card">
          <h3>Build scenario sequences</h3>
          <p class="text">
            Use image-driven prompts to create richer elicitation contexts than plain questionnaire-style prompts.
          </p>
        </article>

        <article class="card">
          <h3>Attach oral responses</h3>
          <p class="text">
            Record and organize audio directly against thumbnails and markers to preserve situated speech.
          </p>
        </article>

        <article class="card">
          <h3>Discuss and refine</h3>
          <p class="text">
            Use discussion threads for notes, glossing, translation, interpretation and collaborative review.
          </p>
        </article>

        <article class="card">
          <h3>Document language use</h3>
          <p class="text">
            Build reusable, inspectable material for low-resource and endangered language work.
          </p>
        </article>

        <article class="card">
          <h3>Publish progressively</h3>
          <p class="text">
            Let projects grow from drafts to shareable resources without forcing a rigid all-at-once workflow.
          </p>
        </article>
      </div>

      <!-- Useful links -->
      <div class="section-heading">
        <div>
          <h2>Useful links</h2>
          <p class="muted">Core project and technical resources.</p>
        </div>
      </div>

      <div class="card-grid">
        <article class="card">
          <h3>Repository</h3>
          <p class="text">Source code and development history.</p>
          <a href="https://github.com/Titiplex/vignette" target="_blank" rel="noreferrer">Open GitHub repository</a>
        </article>

        <article class="card">
          <h3>API documentation</h3>
          <p class="text">Browse endpoints, models and permissions.</p>
          <a href="https://titiplex.github.io/vignette/" target="_blank" rel="noreferrer">Open API documentation</a>
        </article>
      </div>

    </section>
  </main>
</template>

<style scoped>
.about-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.about-card__label {
  display: inline-block;
  font-size: 0.72rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--primary);
}
</style>
