import { useEffect, useRef, useState } from "react";
import { LogOut, Menu, MessageSquarePlus, Wrench } from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { RavenMark } from "./components/RavenMark";
import type { AppState, AuthSession, ChatEntry } from "./lib/types";
import {
  addThread,
  appendAssistantDelta,
  appendEntries,
  appendReasoningDelta,
  appendToolCall,
  attachToolResult,
  buildAssistantEntry,
  buildUserEntry,
  completeAssistantEntry,
  createEmptyState,
  createThread,
  fetchCurrentUser,
  formatTimestamp,
  getThreadTitle,
  loadAppState,
  loadAuthSession,
  login,
  saveAppState,
  saveAuthSession,
  streamPrompt
} from "./services/guildService";

export default function App() {
  const [state, setState] = useState<AppState>(() => loadAppState());
  const [session, setSession] = useState<AuthSession | null>(() => loadAuthSession());
  const [activeThreadId, setActiveThreadId] = useState<string | null>(() => loadAppState().threads[0]?.id ?? null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [draft, setDraft] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [initializing, setInitializing] = useState(true);
  const [authBusy, setAuthBusy] = useState(false);
  const [sending, setSending] = useState(false);
  const transcriptRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    saveAppState(state);
  }, [state]);

  useEffect(() => {
    saveAuthSession(session);
  }, [session]);

  useEffect(() => {
    if (!state.threads.length) {
      setActiveThreadId(null);
      return;
    }

    if (!activeThreadId || !state.threads.some((thread) => thread.id === activeThreadId)) {
      setActiveThreadId(state.threads[0].id);
    }
  }, [activeThreadId, state.threads]);

  useEffect(() => {
    transcriptRef.current?.scrollTo({
      top: transcriptRef.current.scrollHeight,
      behavior: "smooth"
    });
  }, [state, sending]);

  useEffect(() => {
    let cancelled = false;

    async function initialize() {
      if (!session?.token) {
        setInitializing(false);
        return;
      }

      try {
        const current = await fetchCurrentUser(session.token);
        if (!cancelled) {
          setSession(current);
        }
      } catch (error) {
        if (!cancelled) {
          setSession(null);
          setErrorMessage(error instanceof Error ? error.message : "Could not authenticate.");
        }
      } finally {
        if (!cancelled) setInitializing(false);
      }
    }

    void initialize();
    return () => {
      cancelled = true;
    };
  }, [session?.token]);

  const activeThread = state.threads.find((thread) => thread.id === activeThreadId) ?? null;

  async function handleSignIn(username: string, password: string) {
    setAuthBusy(true);
    setErrorMessage(null);
    try {
      const nextSession = await login(username, password);
      setSession(nextSession);
      setInitializing(false);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Sign in failed.");
    } finally {
      setAuthBusy(false);
    }
  }

  function handleNewChat() {
    const nextThread = createThread();
    setState((current) => addThread(current, nextThread));
    setActiveThreadId(nextThread.id);
    setDrawerOpen(false);
    setDraft("");
    setErrorMessage(null);
  }

  async function handleSend() {
    if (!session?.token || sending || !draft.trim()) return;

    const prompt = draft.trim();
    const thread = activeThread ?? createThread();
    const assistant = buildAssistantEntry();
    const userEntry = buildUserEntry(prompt);
    const title = thread.entries.length ? thread.title : getThreadTitle(prompt);

    setErrorMessage(null);
    setSending(true);
    setDrawerOpen(false);
    setDraft("");

    setState((current) => {
      const hasThread = current.threads.some((item) => item.id === thread.id);
      const next = hasThread ? current : addThread(current, thread);
      return appendEntries(next, thread.id, [userEntry, assistant], title);
    });
    setActiveThreadId(thread.id);

    try {
      await streamPrompt(session.token, thread.id, prompt, {
        onEvent: (event) => {
          setState((current) => {
            switch (event.type) {
              case "token":
                return appendAssistantDelta(current, thread.id, assistant.id, event.text);
              case "reasoning":
                return appendReasoningDelta(current, thread.id, assistant.id, event.text);
              case "tool":
                return appendToolCall(current, thread.id, event.name, event.args);
              case "tool_result":
                return attachToolResult(current, thread.id, event.name, event.result);
              case "done":
                return completeAssistantEntry(current, thread.id, assistant.id);
              case "error":
                setErrorMessage(event.message);
                return current;
              case "config":
                return current;
            }
          });
        }
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Request failed.";
      setErrorMessage(message);
      if ((error as { status?: number }).status === 401) {
        setSession(null);
      }
    } finally {
      setSending(false);
    }
  }

  function handleSignOut() {
    setSession(null);
    setState(createEmptyState());
    setActiveThreadId(null);
    setDrawerOpen(false);
    setDraft("");
    setErrorMessage(null);
  }

  if (initializing) {
    return <div className="app-loading">Loading Hugin...</div>;
  }

  if (!session) {
    return <SignInScreen busy={authBusy} message={errorMessage} onSubmit={handleSignIn} />;
  }

  return (
    <main className="app-shell">
      <button className={`app-backdrop ${drawerOpen ? "visible" : ""}`} onClick={() => setDrawerOpen(false)} aria-label="Close chat history" />

      <aside className={`history-drawer ${drawerOpen ? "open" : ""}`}>
        <div className="history-header">
          <div>
            <div className="history-label">History</div>
            <h2>Chats</h2>
          </div>
          <button className="icon-button" onClick={handleNewChat} aria-label="Start a new chat">
            <MessageSquarePlus size={18} />
          </button>
        </div>

        <div className="history-list">
          {state.threads.length ? (
            state.threads.map((thread) => (
              <button
                key={thread.id}
                className={`history-item ${thread.id === activeThreadId ? "active" : ""}`}
                onClick={() => {
                  setActiveThreadId(thread.id);
                  setDrawerOpen(false);
                }}
              >
                <span className="history-item-title">{thread.title}</span>
                <span className="history-item-time">{formatTimestamp(thread.updatedAt)}</span>
              </button>
            ))
          ) : (
            <div className="history-empty">No chat history yet.</div>
          )}
        </div>
      </aside>

      <section className="chat-shell">
        <header className="topbar">
          <div className="topbar-left">
            <button className="icon-button" onClick={() => setDrawerOpen((open) => !open)} aria-label="Toggle chat history">
              <Menu size={18} />
            </button>
            <div className="brand-lockup">
              <div className="brand-mark">
                <RavenMark className="brand-mark-icon" />
              </div>
              <div>
                <div className="brand-kicker">Hugin</div>
                <div className="brand-title">Agent chat</div>
              </div>
            </div>
          </div>

          <div className="topbar-right">
            <span className="session-chip">{session.username}</span>
            <button className="icon-button" onClick={handleSignOut} aria-label="Sign out">
              <LogOut size={18} />
            </button>
          </div>
        </header>

        <div className="chat-panel">
          {errorMessage ? <div className="error-banner">{errorMessage}</div> : null}

          <div className="chat-transcript" ref={transcriptRef}>
            {activeThread ? (
              activeThread.entries.length ? (
                activeThread.entries.map((entry) => <ChatEntryCard key={entry.id} entry={entry} />)
              ) : (
                <EmptyChatState />
              )
            ) : (
              <EmptyChatState />
            )}
          </div>

          <form
            className="chat-composer"
            onSubmit={(event) => {
              event.preventDefault();
              void handleSend();
            }}
          >
            <textarea
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="Send a prompt to the agent..."
              rows={1}
              disabled={sending}
            />
            <button type="submit" disabled={sending || !draft.trim()}>
              {sending ? "Streaming..." : "Send"}
            </button>
          </form>
        </div>
      </section>
    </main>
  );
}

function SignInScreen({
  busy,
  message,
  onSubmit
}: {
  busy: boolean;
  message: string | null;
  onSubmit: (username: string, password: string) => Promise<void>;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  return (
    <main className="signin-shell">
      <section className="signin-card">
        <div className="signin-brand">
          <div className="signin-mark">
            <RavenMark className="brand-mark-icon" />
          </div>
          <div>
            <div className="brand-kicker">Hugin</div>
            <h1>Sign in</h1>
            <p>The frontend has been reduced to the essentials: authenticate, open a chat, stream the run.</p>
          </div>
        </div>

        <form
          className="signin-form"
          onSubmit={(event) => {
            event.preventDefault();
            void onSubmit(username, password);
          }}
        >
          <label>
            <span>Username</span>
            <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
          </label>
          <label>
            <span>Password</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
            />
          </label>
          {message ? <div className="error-banner compact">{message}</div> : null}
          <button type="submit" disabled={busy || !username.trim() || !password}>
            {busy ? "Signing in..." : "Sign in"}
          </button>
        </form>
      </section>
    </main>
  );
}

function EmptyChatState() {
  return (
    <div className="empty-chat">
      <div className="empty-chat-mark">
        <RavenMark className="brand-mark-icon" />
      </div>
      <h2>Start a chat</h2>
      <p>Send a prompt to open a thread. Agent replies stream live, and tool calls appear inline as expandable records.</p>
    </div>
  );
}

function ChatEntryCard({ entry }: { entry: ChatEntry }) {
  if (entry.type === "tool") {
    return (
      <article className="entry-card tool">
        <div className="entry-head">
          <div className="entry-icon">
            <Wrench size={16} />
          </div>
          <div>
            <strong>{entry.tool.name}</strong>
            <div className="entry-time">{formatTimestamp(entry.createdAt)}</div>
          </div>
        </div>
        <details className="tool-disclosure">
          <summary>Open full tool call</summary>
          <pre>{entry.tool.args || "(no arguments)"}</pre>
          {entry.tool.result ? (
            <>
              <div className="tool-result-label">Result</div>
              <pre>{entry.tool.result}</pre>
            </>
          ) : (
            <div className="tool-result-label">Running...</div>
          )}
        </details>
      </article>
    );
  }

  return (
    <article className={`entry-card ${entry.type}`}>
      <div className="entry-head">
        <div className="entry-icon">{entry.type === "assistant" ? <RavenMark className="entry-raven" /> : "You"}</div>
        <div>
          <strong>{entry.type === "assistant" ? "Hugin" : "You"}</strong>
          <div className="entry-time">{formatTimestamp(entry.createdAt)}</div>
        </div>
      </div>

      <div className="entry-body">
        {entry.type === "assistant" ? (
          <>
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{entry.content || " "}</ReactMarkdown>
            {entry.reasoning ? (
              <details className="tool-disclosure">
                <summary>Open reasoning stream</summary>
                <pre>{entry.reasoning}</pre>
              </details>
            ) : null}
          </>
        ) : (
          <p>{entry.content}</p>
        )}
      </div>
    </article>
  );
}
