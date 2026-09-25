// Thin client for the backend REST API.
// Dev auth: a token minted by the backend's /dev/tokens endpoint is kept in localStorage.
// Production should replace this with an OIDC login (e.g. Auth.js) and short-lived tokens.

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

const TOKEN_KEY = "assessment.token";
const ROLE_KEY = "assessment.role";

export type Role = "ADMIN" | "CANDIDATE";

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function getRole(): Role | null {
  try {
    return localStorage.getItem(ROLE_KEY) as Role | null;
  } catch {
    return null;
  }
}

export function saveSession(token: string, role: Role) {
  try {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(ROLE_KEY, role);
  } catch {
    // Private mode: the session lasts until reload.
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLE_KEY);
  } catch {
    // ignore
  }
}

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");

  const res = await fetch(`${API_URL}${path}`, { ...init, headers });
  if (!res.ok) {
    let message = res.statusText;
    try {
      const problem = await res.json();
      message = problem.detail ?? problem.title ?? message;
    } catch {
      // not JSON
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204 || res.headers.get("Content-Length") === "0") {
    return undefined as T;
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function devLogin(role: Role, subject: string, candidateId?: string) {
  const { token } = await api<{ token: string }>("/dev/tokens", {
    method: "POST",
    body: JSON.stringify({ subject, roles: [role], candidateId }),
  });
  saveSession(token, role);
}
