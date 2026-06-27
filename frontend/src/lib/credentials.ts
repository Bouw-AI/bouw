// The Credential Management API isn't in the default TS DOM lib, so declare the slice we use.
interface PasswordCredentialData {
  id: string;
  password: string;
  name?: string;
  iconURL?: string;
}

declare global {
  // eslint-disable-next-line no-var
  var PasswordCredential: {
    prototype: Credential;
    new (data: PasswordCredentialData): Credential;
  };
}

/**
 * Ask the browser to remember the just-used credential after a successful sign-in.
 *
 * Our auth is two-step: a form with the email + password emails a 6-digit code, and a *second* form
 * (only the email + the code) actually establishes the session. By the time sign-in succeeds the
 * password field is long gone from the DOM and there was never a navigation, so Chrome's heuristic
 * for "a login form was submitted → offer to save the password" never fires. We close that gap by
 * handing the credential to the Credential Management API ourselves, which makes Chromium show its
 * native "Save password?" prompt.
 *
 * Best-effort by design: it's a silent no-op on browsers without the API (Firefox/Safari rely on
 * their own heuristics) and in non-secure contexts, and it never throws into the sign-in path.
 */
export async function offerToSavePassword(username: string, password: string): Promise<void> {
  if (!username || !password) return;
  if (typeof window === "undefined" || !("PasswordCredential" in window)) return;
  if (!navigator.credentials?.store) return;
  try {
    const credential = new PasswordCredential({ id: username, password });
    await navigator.credentials.store(credential);
  } catch {
    // Saving a credential must never block or fail the sign-in.
  }
}
