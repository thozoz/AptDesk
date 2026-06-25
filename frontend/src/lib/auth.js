// Client-side auth gate — LAN-trusted model, unchanged from v0.3.0 (D-11).
// No password validation, hashing, or server round-trip; presence of the
// aptdesk.user key in localStorage is the whole gate. Future hardening is
// deferred (REQUIREMENTS.md AUTH-01).

const KEY = 'aptdesk.user';

export function isLoggedIn() {
  try {
    return !!localStorage.getItem(KEY);
  } catch {
    return false;
  }
}

export function login(username) {
  localStorage.setItem(KEY, username);
}

export function logout() {
  localStorage.removeItem(KEY);
}
