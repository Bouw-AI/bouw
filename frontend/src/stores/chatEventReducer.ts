import type { ChatActivity, ChatAttachment, ChatEntry, ChatRun, ChatThread } from "../lib/types";
import type { ChatEvent } from "../services/guildService";

/**
 * Pure, idempotent projection of the backend chat event log into a {@link ChatThread}.
 *
 * Invariant: the same ordered event list MUST produce the exact same UI projection whether it
 * arrives from `GET /events` or live through SSE. Achieving this requires that every reduction
 * be a deterministic function of (thread, event) with no reliance on wall-clock or insertion path.
 *
 * Deduplication: events are keyed by their backend `seq`, which is monotonically increasing and
 * unique per session. Re-applying an event whose seq is not strictly greater than the highest seq
 * already folded into the thread is a no-op. Structural events (messages) are additionally keyed by
 * `messageId` so a re-applied "started" event cannot create a second bubble. Together this makes
 * the reducer idempotent under duplicate delivery (fetch + stream overlap, SSE retries, etc.).
 *
 * Projection boundary:
 *   - Chat projection (`entries`): user + assistant natural-language messages only.
 *   - Activity projection (`activities`): tool calls, run lifecycle, and any other low-level /
 *     status / debug events. Tool output never enters the main chat.
 */

const CHAT_EVENT_TYPES = new Set<string>([
  "user_message_created",
  "assistant_message_started",
  "assistant_token",
  "assistant_reasoning",
  "assistant_message_completed",
  "assistant_message_error"
]);

/**
 * Classifier for the chat/activity boundary.
 *
 * Today the backend emits tool output exclusively through `tool_call_*` events, so the rule is
 * simply "anything that is not a user/assistant message is activity". If a future (or legacy)
 * backend ever encodes tool output as an `assistant_*` message, extend this classifier to detect
 * and divert those payloads — this is the single documented seam for that workaround.
 */
export function isActivityEvent(type: string): boolean {
  return !CHAT_EVENT_TYPES.has(type);
}

function metadataString(event: ChatEvent, key: string): string | undefined {
  const raw = event.metadata?.[key];
  return typeof raw === "string" && raw ? raw : undefined;
}

function activityLabel(event: ChatEvent): string {
  const name = metadataString(event, "name") ?? event.type;
  switch (event.type) {
    case "run_started":
      return "Run started";
    case "run_completed":
      return "Run completed";
    case "run_error":
      return "Run failed";
    case "tool_call_started":
      return `Tool started: ${name}`;
    case "tool_call_completed":
      return `Tool completed: ${name}`;
    case "tool_call_error":
      return `Tool failed: ${name}`;
    default:
      return event.type.replaceAll("_", " ");
  }
}

function activityDetail(event: ChatEvent): string | undefined {
  return (
    metadataString(event, "result")
    ?? metadataString(event, "args")
    ?? metadataString(event, "message")
    ?? event.content
    ?? undefined
  );
}

function activityStatus(event: ChatEvent): ChatActivity["status"] {
  if (event.type.endsWith("_error") || event.type === "run_error") return "error";
  if (event.type.endsWith("_completed") || event.type === "run_completed") return "completed";
  if (event.type.endsWith("_started") || event.type === "run_started") return "running";
  return "info";
}

function buildActivity(event: ChatEvent): ChatActivity {
  return {
    id: event.id,
    runId: event.runId ?? undefined,
    type: event.type,
    label: activityLabel(event),
    status: activityStatus(event),
    detail: activityDetail(event),
    createdAt: event.createdAt
  };
}

function reduceRun(run: ChatRun | null, event: ChatEvent): ChatRun | null {
  const runId = event.runId ?? run?.id ?? null;
  switch (event.type) {
    case "run_started":
      return { id: runId, status: "running", startedAt: event.createdAt };
    case "run_completed":
      return { ...(run ?? { id: runId }), id: runId, status: "completed", completedAt: event.createdAt };
    case "run_error":
      return {
        ...(run ?? { id: runId }),
        id: runId,
        status: "failed",
        completedAt: event.createdAt,
        error: metadataString(event, "message") ?? event.content ?? "The run failed."
      };
    default:
      return run;
  }
}

function asAssistant(entry: ChatEntry | undefined): Extract<ChatEntry, { type: "assistant" }> | null {
  return entry?.type === "assistant" ? entry : null;
}

function reduceMessageEntry(entries: ChatEntry[], event: ChatEvent): ChatEntry[] {
  const messageId = event.messageId ?? undefined;
  const next = entries.slice();
  const index = messageId ? next.findIndex((entry) => entry.id === messageId) : -1;
  const eventAttachments = Array.isArray(event.metadata?.attachments)
    ? (event.metadata?.attachments as ChatAttachment[])
    : undefined;

  switch (event.type) {
    case "user_message_created": {
      if (!messageId || index !== -1) break;
      // Reconcile an optimistic pending draft (frontend id) into the confirmed backend message.
      const pendingIndex = next.findIndex(
        (entry) => entry.type === "user" && entry.pending && entry.content === (event.content ?? "")
      );
      const pending = pendingIndex !== -1 ? next[pendingIndex] : undefined;
      const attachments = eventAttachments?.length
        ? eventAttachments
        : pending?.type === "user"
          ? pending.attachments
          : undefined;
      const entry: ChatEntry = {
        id: messageId,
        type: "user",
        content: event.content ?? "",
        ...(attachments?.length ? { attachments } : {}),
        createdAt: event.createdAt
      };
      if (pendingIndex !== -1) {
        next[pendingIndex] = entry;
      } else {
        next.push(entry);
      }
      break;
    }
    case "assistant_message_started": {
      if (messageId && index === -1) {
        next.push({ id: messageId, type: "assistant", content: "", reasoning: "", createdAt: event.createdAt });
      }
      break;
    }
    case "assistant_reasoning": {
      if (!messageId) break;
      const current = asAssistant(next[index]);
      if (index === -1) {
        next.push({ id: messageId, type: "assistant", content: "", reasoning: event.content ?? "", createdAt: event.createdAt });
      } else if (current) {
        next[index] = { ...current, reasoning: `${current.reasoning}${event.content ?? ""}` };
      }
      break;
    }
    case "assistant_token": {
      if (!messageId) break;
      const current = asAssistant(next[index]);
      if (index === -1) {
        next.push({ id: messageId, type: "assistant", content: event.content ?? "", reasoning: "", createdAt: event.createdAt });
      } else if (current) {
        next[index] = { ...current, content: `${current.content}${event.content ?? ""}` };
      }
      break;
    }
    case "assistant_message_completed": {
      const current = asAssistant(next[index]);
      if (current) {
        next[index] = { ...current, content: current.content || event.content || "", completedAt: event.createdAt };
      }
      break;
    }
    case "assistant_message_error": {
      const current = asAssistant(next[index]);
      if (index === -1 && messageId) {
        next.push({
          id: messageId,
          type: "assistant",
          content: event.content ?? "The run failed.",
          reasoning: "",
          createdAt: event.createdAt,
          completedAt: event.createdAt
        });
      } else if (current) {
        next[index] = {
          ...current,
          content: current.content || event.content || "The run failed.",
          completedAt: event.createdAt
        };
      }
      break;
    }
    default:
      break;
  }

  return next;
}

/** Fold a single event into a thread. Idempotent and deterministic. */
export function reduceChatEvent(thread: ChatThread, event: ChatEvent): ChatThread {
  // Seq is the per-session monotonic dedup key. Anything not strictly newer was already applied.
  if ((thread.lastSeq ?? 0) >= event.seq) {
    return thread;
  }

  let entries = thread.entries;
  let activities = thread.activities ?? [];
  let run = thread.run ?? null;

  if (isActivityEvent(event.type)) {
    activities = [...activities, buildActivity(event)];
    run = reduceRun(run, event);
  } else {
    entries = reduceMessageEntry(entries, event);
    if (event.type === "assistant_message_error" && run && run.status !== "completed") {
      run = {
        ...run,
        status: "failed",
        completedAt: event.createdAt,
        error: run.error ?? event.content ?? "The run failed."
      };
    }
  }

  return {
    ...thread,
    entries,
    activities,
    run,
    lastSeq: event.seq,
    updatedAt: event.createdAt
  };
}

/** Reset only the event-derived projection, preserving thread metadata. */
export function resetProjection(thread: ChatThread): ChatThread {
  return { ...thread, entries: [], activities: [], run: null, lastSeq: 0 };
}

/** Fold an ordered list of events. With `replace`, rebuild the projection from scratch. */
export function reduceChatEvents(
  thread: ChatThread,
  events: ChatEvent[],
  options?: { replace?: boolean }
): ChatThread {
  const start = options?.replace ? resetProjection(thread) : thread;
  return events.reduce(reduceChatEvent, start);
}

/** Whether the thread currently has an in-flight run; drives send/stop/loading affordances. */
export function isThreadBusy(thread: ChatThread): boolean {
  const status = thread.run?.status;
  if (status === "completed" || status === "failed") return false;
  if (status === "queued" || status === "running" || status === "cancelling") return true;
  // Legacy fallback for threads without run events: an assistant bubble still streaming.
  return thread.entries.some((entry) => entry.type === "assistant" && !entry.completedAt);
}
