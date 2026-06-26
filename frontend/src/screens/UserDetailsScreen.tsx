import { useEffect, useState } from "react";

import { AppHeader } from "../components/AppHeader";
import { updateUserProfile } from "../services/apiClient";
import type { AuthSession } from "../lib/types";

export function UserDetailsScreen(props: {
  session: AuthSession;
  onBack: () => void;
  onMenu: () => void;
  onResetPassword: () => void;
  onSessionUpdate: (updated: Partial<AuthSession>) => void;
}) {
  const { session, onBack, onMenu, onResetPassword, onSessionUpdate } = props;

  const [displayName, setDisplayName] = useState(session.displayName ?? "");
  const [email, setEmail] = useState(session.email ?? "");
  const [customInstructions, setCustomInstructions] = useState(session.customInstructions ?? "");

  const [savingProfile, setSavingProfile] = useState(false);
  const [profileSaved, setProfileSaved] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);

  // Re-seed fields when the session updates after a successful save.
  useEffect(() => {
    setDisplayName(session.displayName ?? "");
    setEmail(session.email ?? "");
    setCustomInstructions(session.customInstructions ?? "");
  }, [session.displayName, session.email, session.customInstructions]);

  const handleSaveProfile = async () => {
    if (savingProfile) return;
    setSavingProfile(true);
    setProfileError(null);
    setProfileSaved(false);
    try {
      const updated = await updateUserProfile(session.token, {
        displayName: displayName.trim() || null,
        email: email.trim() || null,
        customInstructions: customInstructions.trim() || null
      });
      onSessionUpdate({
        displayName: updated.displayName,
        email: updated.email,
        customInstructions: updated.customInstructions
      });
      setProfileSaved(true);
      window.setTimeout(() => setProfileSaved(false), 3000);
    } catch (e) {
      setProfileError(e instanceof Error ? e.message : "Could not save profile.");
    } finally {
      setSavingProfile(false);
    }
  };

  return (
    <div className="settings-body">
      <AppHeader backAction={{ onClick: onBack }} title="Account" onMenu={onMenu} />

      <div className="settings-section">
        <div className="history-group-label">PROFILE</div>
        <p className="settings-hint">Your username cannot be changed.</p>

        <label className="composer-select settings-select">
          <span>Username</span>
          <input type="text" value={session.username} disabled readOnly className="settings-number-input" />
        </label>

        <label className="composer-select settings-select">
          <span>Display name</span>
          <input
            type="text"
            placeholder="Your name"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            maxLength={200}
            className="settings-number-input"
          />
        </label>

        <label className="composer-select settings-select">
          <span>Email</span>
          <input
            type="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            maxLength={255}
            className="settings-number-input"
          />
        </label>
      </div>

      <div className="settings-section">
        <div className="history-group-label">CUSTOM INSTRUCTIONS</div>
        <p className="settings-hint">
          Standing instructions injected into every agent session — e.g. preferred coding style, output
          format, or domain context.
        </p>
        <textarea
          className="settings-textarea"
          placeholder="Always respond in British English and prefer functional programming patterns..."
          value={customInstructions}
          onChange={(e) => setCustomInstructions(e.target.value)}
          rows={5}
        />
      </div>

      {profileError && <p className="login-error">{profileError}</p>}
      {profileSaved && <p className="screen-note">Profile saved.</p>}

      <div className="settings-section settings-actions">
        <button
          type="button"
          className="primary-button"
          onClick={handleSaveProfile}
          disabled={savingProfile}
        >
          {savingProfile ? "Saving…" : "Save profile"}
        </button>
      </div>

      <div className="settings-section">
        <div className="history-group-label">PASSWORD</div>
        <p className="settings-hint">
          Reset your password with email verification — we&apos;ll email a 6-digit code to confirm the
          change before it&apos;s saved.
        </p>
        <button type="button" className="secondary-button settings-manage-button" onClick={onResetPassword}>
          Reset password
        </button>
      </div>
    </div>
  );
}
