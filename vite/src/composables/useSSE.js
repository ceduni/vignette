import { ref, onUnmounted } from "vue";
import { buildApiUrl } from "../api/rest";

export function useSSE(path, { onMessage, onError } = {}) {
  const connected = ref(false);
  let es = null;
  let retryTimeout = null;
  let retryDelay = 2000;
  let stopped = false;

  function connect() {
    if (stopped) return;
    try {
      es = new EventSource(buildApiUrl(path), { withCredentials: true });

      es.onopen = () => {
        connected.value = true;
        retryDelay = 2000; // reset backoff on success
      };

      es.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          onMessage?.(data);
        } catch {
          onMessage?.(event.data);
        }
      };

      es.addEventListener("notification", (event) => {
        try {
          const data = JSON.parse(event.data);
          onMessage?.(data);
        } catch {
          onMessage?.(event.data);
        }
      });

      es.onerror = () => {
        connected.value = false;
        es?.close();
        es = null;
        onError?.();
        if (!stopped) {
          retryTimeout = setTimeout(() => {
            retryDelay = Math.min(retryDelay * 1.5, 30000); // exponential backoff, max 30s
            connect();
          }, retryDelay);
        }
      };
    } catch {
      connected.value = false;
    }
  }

  function disconnect() {
    stopped = true;
    clearTimeout(retryTimeout);
    es?.close();
    es = null;
    connected.value = false;
  }

  onUnmounted(disconnect);

  return { connect, disconnect, connected };
}