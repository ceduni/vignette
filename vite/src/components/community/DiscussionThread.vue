<script setup>
import {computed, onMounted, ref, watch} from "vue";
import {RouterLink} from "vue-router";
import {CONTRIBUTION_TYPES, createDiscussionMessage, fetchDiscussionMessages} from "../../api/community";
import {useAuth} from "../../composables/useAuth";
import {useToast} from "../../composables/useToast";
import BaseAlert from "../ui/BaseAlert.vue";
import BaseBadge from "../ui/BaseBadge.vue";
import BaseEmptyState from "../ui/BaseEmptyState.vue";

const props = defineProps({
  title: {type: String, default: "Discussion"},
  subtitle: {type: String, default: ""},
  targetType: {type: String, required: true},
  targetId: {type: [String, Number], required: true},
  emptyTitle: {type: String, default: "No messages yet"},
  emptyMessage: {type: String, default: "Start the first discussion."},
});

const {isAuthenticated, loadMe} = useAuth();
const toast = useToast();

const loading = ref(false);
const submitting = ref(false);
const error = ref("");
const messages = ref([]);

const content = ref("");
const contributionType = ref("GENERAL");
const replyTo = ref(null);

const normalizedTargetId = computed(() => String(props.targetId ?? ""));

const contextualContributionTypes = computed(() => {
  if (props.targetType === "LANGUAGE") {
    return [
      {value: "GENERAL", label: "General note"},
      {value: "TRANSCRIPTION", label: "Transcription note"},
      {value: "TRANSLATION", label: "Translation note"},
      {value: "GLOSS", label: "Glossing note"},
      {value: "INTERPRETATION", label: "Interpretation / analysis"},
    ];
  }
  return CONTRIBUTION_TYPES;
});

const messagesById = computed(() => {
  const map = new Map();
  for (const message of messages.value) {
    map.set(String(message.id), message);
  }
  return map;
});

const threadedMessages = computed(() => {
  const children = new Map();
  const roots = [];

  for (const message of messages.value) {
    const parentId = message.parentMessageId == null ? null : String(message.parentMessageId);
    if (!parentId || !messagesById.value.has(parentId)) {
      roots.push(message);
      continue;
    }
    if (!children.has(parentId)) children.set(parentId, []);
    children.get(parentId).push(message);
  }

  const flat = [];
  function visit(message, depth = 0) {
    flat.push({...message, _depth: depth});
    for (const child of children.get(String(message.id)) || []) {
      visit(child, depth + 1);
    }
  }
  for (const root of roots) visit(root, 0);
  return flat;
});

// Initiales de l'auteur pour l'avatar
function authorInitials(username) {
  if (!username) return "?";
  return username.slice(0, 2).toUpperCase();
}

// Couleur d'avatar déterministe basée sur le nom
function avatarColor(username) {
  const colors = [
    "#0f766e", "#0b5f59", "#1d4e89", "#6d28d9",
    "#b45309", "#065f46", "#9d174d", "#1e40af",
  ];
  if (!username) return colors[0];
  const index = username.charCodeAt(0) % colors.length;
  return colors[index];
}

function contributionLabel(value) {
  return contextualContributionTypes.value.find((item) => item.value === value)?.label ?? value ?? "General";
}

function contributionVariant(value) {
  const map = {
    GENERAL: "neutral",
    TRANSCRIPTION: "info",
    TRANSLATION: "success",
    GLOSS: "warning",
    INTERPRETATION: "info",
  };
  return map[value] ?? "neutral";
}

function formatDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, {
    year: "numeric", month: "short", day: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

function setReply(message) {
  replyTo.value = message;
  // Scroll vers le composer
  document.querySelector(".discussion-thread__composer")?.scrollIntoView({behavior: "smooth", block: "nearest"});
}

function clearReply() {
  replyTo.value = null;
}

async function loadThread() {
  if (!props.targetType || !normalizedTargetId.value) {
    messages.value = [];
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    messages.value = await fetchDiscussionMessages(props.targetType, normalizedTargetId.value);
  } catch (e) {
    error.value = e.message || "Failed to load discussion.";
  } finally {
    loading.value = false;
  }
}

async function submit() {
  if (!content.value.trim()) return;
  submitting.value = true;
  error.value = "";
  try {
    const created = await createDiscussionMessage({
      targetType: props.targetType,
      targetId: normalizedTargetId.value,
      parentMessageId: replyTo.value?.id ?? null,
      contributionType: contributionType.value,
      content: content.value.trim(),
    });
    messages.value = [...messages.value, created];
    content.value = "";
    contributionType.value = "GENERAL";
    replyTo.value = null;
    toast.success("Message posted.");
  } catch (e) {
    error.value = e.message || "Failed to post message.";
    toast.error(error.value);
  } finally {
    submitting.value = false;
  }
}

watch(
    () => [props.targetType, normalizedTargetId.value],
    () => {
      replyTo.value = null;
      contributionType.value = "GENERAL";
      loadThread();
    },
    {immediate: true}
);

onMounted(() => {
  loadMe().catch(() => null);
});
</script>

<template>
  <section class="discussion-thread">

    <!-- En-tête -->
    <div class="discussion-thread__header">
      <div class="discussion-thread__header-left">
        <h2 class="discussion-thread__title">{{ title }}</h2>
        <p v-if="subtitle" class="discussion-thread__subtitle">{{ subtitle }}</p>
      </div>
      <div class="discussion-thread__header-right">
        <span class="discussion-thread__count">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          {{ messages.length }}
        </span>
      </div>
    </div>

    <BaseAlert v-if="error" type="error">{{ error }}</BaseAlert>

    <!-- Chargement -->
    <div v-if="loading" class="discussion-thread__loading">
      <span class="loader-spinner"></span>
      <span class="muted">Loading discussion...</span>
    </div>

    <!-- Liste des messages -->
    <div v-else-if="threadedMessages.length" class="discussion-thread__list">
      <article
          v-for="message in threadedMessages"
          :key="message.id"
          class="discussion-message"
          :class="{ 'discussion-message--reply': message._depth > 0 }"
          :style="{ '--discussion-depth': message._depth }"
      >
        <!-- Ligne de fil pour les réponses -->
        <div v-if="message._depth > 0" class="discussion-message__thread-line"></div>

        <div class="discussion-message__inner">
          <!-- Avatar -->
          <div
              class="discussion-message__avatar"
              :style="{ background: avatarColor(message.authorUsername) }"
          >
            {{ authorInitials(message.authorUsername) }}
          </div>

          <div class="discussion-message__body">
            <!-- En-tête du message -->
            <div class="discussion-message__meta">
              <span class="discussion-message__author">{{ message.authorUsername || "Unknown user" }}</span>
              <span class="discussion-message__dot">·</span>
              <span class="discussion-message__date">{{ formatDate(message.createdAt) }}</span>
              <BaseBadge :variant="contributionVariant(message.contributionType)" class="discussion-message__type">
                {{ contributionLabel(message.contributionType) }}
              </BaseBadge>
              <BaseBadge v-if="message.parentMessageId" variant="warning" class="discussion-message__type">
                ↩ Reply
              </BaseBadge>
            </div>

            <!-- Contenu -->
            <p class="discussion-message__content">{{ message.content }}</p>

            <!-- Actions -->
            <div v-if="isAuthenticated" class="discussion-message__actions">
              <button type="button" class="discussion-message__reply-btn" @click="setReply(message)">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="9 17 4 12 9 7"/>
                  <path d="M20 18v-2a4 4 0 0 0-4-4H4"/>
                </svg>
                Reply
              </button>
            </div>
          </div>
        </div>
      </article>
    </div>

    <!-- État vide -->
    <BaseEmptyState
        v-else-if="!loading"
        :title="emptyTitle"
        :message="emptyMessage"
    />

    <!-- Composer -->
    <div class="discussion-thread__composer">

      <!-- Réponse à -->
      <div v-if="replyTo" class="discussion-thread__reply-preview">
        <div class="discussion-thread__reply-preview-inner">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="9 17 4 12 9 7"/>
            <path d="M20 18v-2a4 4 0 0 0-4-4H4"/>
          </svg>
          <span>Replying to <strong>{{ replyTo.authorUsername || "Unknown user" }}</strong></span>
          <span class="discussion-thread__reply-excerpt">{{ replyTo.content.slice(0, 60) }}{{ replyTo.content.length > 60 ? "…" : "" }}</span>
        </div>
        <button type="button" class="discussion-thread__cancel-reply" @click="clearReply" aria-label="Cancel reply">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M18 6 6 18"/><path d="m6 6 12 12"/>
          </svg>
        </button>
      </div>

      <!-- Formulaire authentifié -->
      <template v-if="isAuthenticated">
        <div class="discussion-thread__form">
          <label class="discussion-thread__label">
            Contribution type
            <select v-model="contributionType" class="discussion-thread__select">
              <option v-for="option in contextualContributionTypes" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label class="discussion-thread__label discussion-thread__label--full">
            Message
            <textarea
                v-model="content"
                class="discussion-thread__textarea"
                rows="4"
                placeholder="Write a message, note, question or annotation..."
            />
          </label>
        </div>

        <div class="discussion-thread__toolbar">
          <span class="discussion-thread__char-count" :class="{ 'discussion-thread__char-count--warn': content.length > 800 }">
            {{ content.length }} / 1000
          </span>
          <button
              type="button"
              class="btn btn--primary"
              :disabled="submitting || !content.trim() || content.length > 1000"
              @click="submit"
          >
            <svg v-if="!submitting" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
            {{ submitting ? "Posting..." : "Post message" }}
          </button>
        </div>
      </template>

      <!-- Non authentifié -->
      <template v-else>
        <div class="discussion-thread__guest">
          <div class="discussion-thread__guest-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <div>
            <p class="discussion-thread__guest-title">Join the discussion</p>
            <p class="discussion-thread__guest-sub">Log in to post messages and contribute to this language.</p>
          </div>
          <RouterLink class="btn btn--primary" to="/login">Log in</RouterLink>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.discussion-thread {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* En-tête */
.discussion-thread__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.discussion-thread__title {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text);
}

.discussion-thread__subtitle {
  margin: 0.2rem 0 0;
  font-size: 0.88rem;
  color: var(--text-soft);
}

.discussion-thread__count {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--text-soft);
  background: var(--surface-alt);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 4px 10px;
}

/* Chargement */
.discussion-thread__loading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 1rem 0;
}

/* Liste */
.discussion-thread__list {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

/* Message */
.discussion-message {
  position: relative;
  margin-left: calc(var(--discussion-depth, 0) * 1.5rem);
}

.discussion-message__thread-line {
  position: absolute;
  left: -0.85rem;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--border);
  border-radius: 2px;
}

.discussion-message__inner {
  display: flex;
  gap: 0.75rem;
  padding: 1rem 1.1rem;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: linear-gradient(180deg, #ffffff 0%, #f7faff 100%);
  box-shadow: var(--shadow);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.discussion-message__inner:hover {
  border-color: rgba(15, 118, 110, 0.22);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.1);
}

.discussion-message--reply .discussion-message__inner {
  background: linear-gradient(180deg, #f8fbff 0%, #f1f6ff 100%);
}

/* Avatar */
.discussion-message__avatar {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.78rem;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.02em;
  margin-top: 1px;
}

/* Corps du message */
.discussion-message__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.discussion-message__meta {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.discussion-message__author {
  font-weight: 700;
  font-size: 0.9rem;
  color: var(--text);
}

.discussion-message__dot {
  color: var(--text-soft);
  font-size: 0.8rem;
}

.discussion-message__date {
  font-size: 0.8rem;
  color: var(--text-soft);
}

.discussion-message__type {
  font-size: 0.75rem;
  padding: 2px 8px;
  min-height: 22px;
}

.discussion-message__content {
  margin: 0;
  font-size: 0.93rem;
  line-height: 1.65;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
}

.discussion-message__actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.15rem;
}

.discussion-message__reply-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-soft);
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 6px;
  transition: color 0.15s, background 0.15s;
}

.discussion-message__reply-btn:hover {
  color: var(--primary);
  background: var(--accent-cool);
}

/* Composer */
.discussion-thread__composer {
  border-top: 1px solid var(--border);
  padding-top: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* Prévisualisation de réponse */
.discussion-thread__reply-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.65rem 1rem;
  border-radius: 10px;
  background: var(--accent-cool);
  border-left: 3px solid var(--primary);
}

.discussion-thread__reply-preview-inner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--text);
  flex: 1;
  min-width: 0;
}

.discussion-thread__reply-excerpt {
  color: var(--text-soft);
  font-style: italic;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.discussion-thread__cancel-reply {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--text-soft);
  display: flex;
  align-items: center;
  padding: 4px;
  border-radius: 6px;
  transition: color 0.15s, background 0.15s;
  flex-shrink: 0;
}

.discussion-thread__cancel-reply:hover {
  color: var(--danger);
  background: rgba(180, 35, 24, 0.08);
}

/* Formulaire */
.discussion-thread__form {
  display: grid;
  gap: 0.85rem;
  grid-template-columns: 200px 1fr;
}

@media (max-width: 600px) {
  .discussion-thread__form {
    grid-template-columns: 1fr;
  }
}

.discussion-thread__label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-soft);
}

.discussion-thread__label--full {
  grid-column: 1 / -1;
}

.discussion-thread__select,
.discussion-thread__textarea {
  font-family: inherit;
  font-size: 0.93rem;
  color: var(--text);
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 8px 12px;
  transition: border-color 0.15s, box-shadow 0.15s;
  width: 100%;
}

.discussion-thread__select:focus,
.discussion-thread__textarea:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
}

.discussion-thread__textarea {
  resize: vertical;
  min-height: 100px;
  line-height: 1.6;
}

/* Barre d'envoi */
.discussion-thread__toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
}

.discussion-thread__char-count {
  font-size: 0.8rem;
  color: var(--text-soft);
  font-weight: 500;
}

.discussion-thread__char-count--warn {
  color: #b54708;
}

/* Invité */
.discussion-thread__guest {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.1rem 1.25rem;
  border-radius: var(--radius);
  background: var(--accent-cool);
  border: 1px solid var(--border);
}

.discussion-thread__guest-icon {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: rgba(15, 118, 110, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  flex-shrink: 0;
}

.discussion-thread__guest-title {
  margin: 0;
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--text);
}

.discussion-thread__guest-sub {
  margin: 0.15rem 0 0;
  font-size: 0.83rem;
  color: var(--text-soft);
}
</style>