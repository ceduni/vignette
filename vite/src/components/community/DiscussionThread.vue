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

    if (!children.has(parentId)) {
      children.set(parentId, []);
    }
    children.get(parentId).push(message);
  }

  const flat = [];

  function visit(message, depth = 0) {
    flat.push({...message, _depth: depth});
    for (const child of children.get(String(message.id)) || []) {
      visit(child, depth + 1);
    }
  }

  for (const root of roots) {
    visit(root, 0);
  }

  return flat;
});

function contributionLabel(value) {
  return contextualContributionTypes.value.find((item) => item.value === value)?.label ?? value ?? "General";
}

function formatDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function setReply(message) {
  replyTo.value = message;
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
  <section class="card discussion-thread">
    <div class="section-heading">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="subtitle" class="muted">{{ subtitle }}</p>
      </div>

      <BaseBadge variant="info">
        {{ messages.length }} message(s)
      </BaseBadge>
    </div>

    <BaseAlert v-if="error" type="error">
      {{ error }}
    </BaseAlert>

    <div v-if="loading" class="discussion-thread__loading">
      <p class="muted">Loading discussion...</p>
    </div>

    <div v-else-if="threadedMessages.length" class="discussion-thread__list">
      <article
          v-for="message in threadedMessages"
          :key="message.id"
          class="discussion-message"
          :style="{ '--discussion-depth': message._depth }"
      >
        <div class="discussion-message__header">
          <div>
            <p class="discussion-message__author">
              {{ message.authorUsername || "Unknown user" }}
            </p>
            <p class="muted discussion-message__date">
              {{ formatDate(message.createdAt) }}
            </p>
          </div>

          <div class="discussion-message__badges">
            <BaseBadge variant="neutral">
              {{ contributionLabel(message.contributionType) }}
            </BaseBadge>

            <BaseBadge v-if="message.parentMessageId" variant="warning">
              Reply
            </BaseBadge>
          </div>
        </div>

        <p class="text discussion-message__content">
          {{ message.content }}
        </p>

        <div v-if="isAuthenticated" class="discussion-message__actions">
          <button type="button" class="btn btn--ghost" @click="setReply(message)">
            Reply
          </button>
        </div>
      </article>
    </div>

    <BaseEmptyState
        v-else-if="!loading"
        :title="emptyTitle"
        :message="emptyMessage"
    />

    <section class="discussion-thread__composer">
      <div v-if="replyTo" class="discussion-thread__reply-box">
        <p class="muted">
          Replying to <strong>{{ replyTo.authorUsername || "Unknown user" }}</strong>
        </p>
        <button type="button" class="btn btn--ghost" @click="clearReply">
          Cancel reply
        </button>
      </div>

      <template v-if="isAuthenticated">
        <div class="discussion-thread__form-grid">
          <label>
            Contribution type
            <select v-model="contributionType">
              <option
                  v-for="option in contextualContributionTypes"
                  :key="option.value"
                  :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </label>

          <label class="discussion-thread__textarea">
            Message
            <textarea
                v-model="content"
                rows="4"
                placeholder="Write a message, note, question or annotation..."
            />
          </label>
        </div>

        <div class="toolbar">
          <button
              type="button"
              class="btn btn--primary"
              :disabled="submitting || !content.trim()"
              @click="submit"
          >
            {{ submitting ? "Posting..." : "Post message" }}
          </button>
        </div>
      </template>

      <template v-else>
        <BaseAlert type="info">
          You must be logged in to participate.
        </BaseAlert>

        <div class="toolbar">
          <RouterLink class="btn btn--primary" to="/login">
            Log in to participate
          </RouterLink>
        </div>
      </template>
    </section>
  </section>
</template>

<style scoped>
.discussion-thread {
  display: grid;
  gap: 1rem;
}

.discussion-thread__list {
  display: grid;
  gap: 0.85rem;
}

.discussion-message {
  padding: 1rem;
  border: 1px solid rgba(120, 120, 140, 0.18);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.02);
  margin-left: calc(var(--discussion-depth, 0) * 1rem);
}

.discussion-message__header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
  margin-bottom: 0.6rem;
}

.discussion-message__author {
  margin: 0;
  font-weight: 700;
}

.discussion-message__date {
  margin: 0.2rem 0 0;
}

.discussion-message__badges {
  display: flex;
  gap: 0.45rem;
  flex-wrap: wrap;
}

.discussion-message__content {
  margin: 0;
  white-space: pre-wrap;
}

.discussion-message__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 0.75rem;
}

.discussion-thread__composer {
  display: grid;
  gap: 1rem;
  padding-top: 0.5rem;
  border-top: 1px solid rgba(120, 120, 140, 0.14);
}

.discussion-thread__reply-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 0.85rem 1rem;
  border-radius: 14px;
  background: rgba(120, 120, 140, 0.08);
}

.discussion-thread__form-grid {
  display: grid;
  gap: 1rem;
}

.discussion-thread__textarea textarea {
  min-height: 110px;
  resize: vertical;
}

.discussion-thread__loading {
  padding: 0.5rem 0;
}
</style>