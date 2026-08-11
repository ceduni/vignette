import { ref, computed, watch } from "vue";
import { apiFetch } from "../api/rest";
import { useAuth } from "./useAuth";
import { useSSE } from "./useSSE";

const notifications = ref([]);   // liste complète
const initialized   = ref(false);

export function useNotifications() {
  const { currentUser, isAuthenticated } = useAuth();

  const unreadCount = computed(() =>
    notifications.value.filter(n => !n.read).length
  );

  const hasUnread = computed(() => unreadCount.value > 0);

  const grouped = computed(() => {
    const today     = new Date(); today.setHours(0,0,0,0);
    const yesterday = new Date(today); yesterday.setDate(yesterday.getDate() - 1);

    const groups = { today: [], yesterday: [], older: [] };
    for (const n of notifications.value) {
      const d = new Date(n.createdAt); d.setHours(0,0,0,0);
      if (d >= today)          groups.today.push(n);
      else if (d >= yesterday) groups.yesterday.push(n);
      else                     groups.older.push(n);
    }
    return groups;
  });

  async function fetchNotifications() {
    try {
      const data = await apiFetch("/api/notifications");
      notifications.value = Array.isArray(data) ? data : (data.content ?? []);
      initialized.value = true;
    } catch {
    }
  }

  async function markAsRead(id) {
    const n = notifications.value.find(n => n.id === id);
    if (!n || n.read) return;
    notifications.value = notifications.value.map(item =>
      item.id === id ? { ...item, read: true } : item
    );
    try {
      await apiFetch(`/api/notifications/${id}/read`, { method: "POST" });
    } catch {
      notifications.value = notifications.value.map(item =>
        item.id === id ? { ...item, read: false } : item
      );
    }
  }

  async function markAllAsRead() {
    const prev = notifications.value;
    notifications.value = notifications.value.map(n => ({ ...n, read: true }));
    try {
      await apiFetch("/api/notifications/read-all", { method: "POST" });
    } catch {
      notifications.value = prev;
    }
  }

  async function deleteNotification(id) {
    const prev = notifications.value;
    notifications.value = notifications.value.filter(n => n.id !== id);
    try {
      await apiFetch(`/api/notifications/${id}`, { method: "DELETE" });
    } catch {
      notifications.value = prev;
    }
  }

  async function deleteAllNotifications() {
    const prev = notifications.value;
    notifications.value = [];
    try {
      await apiFetch("/api/notifications", { method: "DELETE" });
    } catch {
      notifications.value = prev;
    }
  }

  const { connect, disconnect, connected } = useSSE("/api/notifications/stream", {
    onMessage(data) {
      if (!data?.id) return;
      const exists = notifications.value.some(n => n.id === data.id);
      if (!exists) {
        notifications.value = [data, ...notifications.value];
      }
    },
  });

  function init() {
    if (!isAuthenticated.value) return;
    fetchNotifications();
    connect();
  }

  function teardown() {
    disconnect();
    notifications.value = [];
    initialized.value = false;
  }

  watch(() => currentUser.value?.username, (username) => {
    teardown();
    if (username) init();
  });

  function iconForType(type) {
    switch (type) {
      case "NEW_SCENARIO_IN_FOLLOWED_LANGUAGE": return "scenario";
      case "NEW_LIKE":       return "like";
      case "NEW_BOOKMARK":   return "bookmark";
      case "NEW_FOLLOWER":   return "follow";
      case "FORK_REVIEW_REQUESTED": return "review";
      case "FORK_APPROVED":         return "check";
      case "FORK_REJECTED":         return "cross";
      case "SYSTEM":         return "system";
      case "COLLABORATION_INVITE":  return "invite";
      case "COLLABORATION_ACCEPTED": return "check";
      default:               return "bell";
    }
  }

  function labelForType(type) {
    switch (type) {
      case "NEW_SCENARIO_IN_FOLLOWED_LANGUAGE": return "New scenario";
      case "NEW_LIKE":     return "New like";
      case "NEW_BOOKMARK": return "New bookmark";
      case "NEW_FOLLOWER": return "New follower";
      case "FORK_REVIEW_REQUESTED": return "Copy pending review";
      case "FORK_APPROVED":         return "Copy approved";
      case "FORK_REJECTED":         return "Copy rejected";
      case "SYSTEM":       return "System";
      case "COLLABORATION_INVITE":   return "Collaboration invite";
      case "COLLABORATION_ACCEPTED": return "Invite accepted";
      default:             return "Notification";
    }
  }

  function formatTime(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    const now = new Date();
    const diff = Math.floor((now - d) / 1000);
    if (diff < 60)   return "just now";
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  }

  return {
    notifications,
    unreadCount,
    hasUnread,
    grouped,
    connected,
    initialized,
    init,
    teardown,
    fetchNotifications,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    deleteAllNotifications,
    iconForType,
    labelForType,
    formatTime,
  };
}