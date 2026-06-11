import { useState } from "react";
import { Field, Button, Card } from "../components/Ui";
import { RavenMark } from "../components/RavenMark";

export function SignInScreen({
  busy = false,
  message,
  onSubmit
}: {
  busy?: boolean;
  message?: string | null;
  onSubmit: (username: string, password: string) => Promise<void>;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  return (
    <main className="signin-shell">
      <Card className="signin-card">
        <div className="signin-brand">
          <div className="new-chat-mark">
            <RavenMark className="new-chat-mark-icon" />
          </div>
          <div>
            <div className="eyebrow">Hugin</div>
            <h1>Sign in</h1>
            <p>Use the account configured for this Hugin server.</p>
          </div>
        </div>

        <form
          className="signin-form"
          onSubmit={(event) => {
            event.preventDefault();
            void onSubmit(username, password);
          }}
        >
          <Field label="Username" value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
          <Field
            label="Password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
          />
          {message ? <div className="signin-message">{message}</div> : null}
          <Button type="submit" disabled={busy || !username.trim() || !password}>
            {busy ? "Signing In..." : "Sign In"}
          </Button>
        </form>
      </Card>
    </main>
  );
}
