import { type RefObject } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Image as ImageIcon } from "lucide-react";

import type { ChatEntry } from "../../lib/types";

function normalizeAssistantMarkdown(content: string) {
  return content.replace(/<br\s*\/?>/gi, "\n");
}

function TypingDots() {
  return (
    <span className="typing-dots">
      <span className="dot" />
      <span className="dot" />
      <span className="dot" />
    </span>
  );
}

/**
 * Renders the main chat transcript: user and assistant natural-language messages only. Tool calls,
 * run lifecycle, and other low-level activity are projected into the ActivityPanel, never here.
 */
export function MessageList({
  entries,
  busy,
  listRef
}: {
  entries: ChatEntry[];
  busy: boolean;
  listRef: RefObject<HTMLDivElement>;
}) {
  return (
    <div ref={listRef} className="messages">
      {entries.map((entry) => {
        if (entry.type === "user") {
          return (
            <div key={entry.id} className="message-row message-row-user fade-in">
              <div className="message-bubble message-bubble-user">
                {entry.attachments?.map((attachment) =>
                  attachment.dataUrl ? (
                    <img
                      key={`${entry.id}-${attachment.name}`}
                      src={attachment.dataUrl}
                      alt={attachment.name}
                      className="message-image"
                    />
                  ) : (
                    <div key={`${entry.id}-${attachment.name}`} className="message-attachment-placeholder">
                      <ImageIcon size={14} strokeWidth={2} />
                      <span>{attachment.name}</span>
                    </div>
                  )
                )}
                {entry.content ? <div>{entry.content}</div> : null}
              </div>
            </div>
          );
        }

        if (entry.type !== "assistant") {
          // Tool entries are no longer part of the chat projection; ignore any legacy ones defensively.
          return null;
        }

        const empty = !entry.content && !entry.reasoning;
        return (
          <div key={entry.id} className="message-row message-row-assistant fade-in">
            <div className="assistant-response">
              {empty && busy ? (
                <TypingDots />
              ) : (
                <>
                  {entry.reasoning ? <div className="assistant-reasoning">{entry.reasoning}</div> : null}
                  {entry.content ? (
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{normalizeAssistantMarkdown(entry.content)}</ReactMarkdown>
                  ) : null}
                </>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
