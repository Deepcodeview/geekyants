"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { devLogin, type Role } from "@/lib/api";

/** Development sign-in. Replace with the organization's OIDC login in production. */
export default function SignIn() {
  const router = useRouter();
  const [role, setRole] = useState<Role>("ADMIN");
  const [subject, setSubject] = useState("admin@example.com");
  const [candidateId, setCandidateId] = useState("CAND-7821");
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    try {
      await devLogin(role, subject, role === "CANDIDATE" ? candidateId : undefined);
      router.push(role === "ADMIN" ? "/admin" : "/candidate");
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  return (
    <main className="container" style={{ maxWidth: 440 }}>
      <div className="card">
        <h1>Sign in</h1>
        <p className="muted small">Development sign-in. The backend must run with the <code>dev</code> profile.</p>
        <form onSubmit={submit}>
          <label htmlFor="role">Role</label>
          <select id="role" value={role} onChange={(e) => setRole(e.target.value as Role)}>
            <option value="ADMIN">Admin</option>
            <option value="CANDIDATE">Candidate</option>
          </select>
          <label htmlFor="subject">User</label>
          <input id="subject" value={subject} onChange={(e) => setSubject(e.target.value)} required />
          {role === "CANDIDATE" && (
            <>
              <label htmlFor="candidateId">Candidate ID</label>
              <input id="candidateId" value={candidateId} onChange={(e) => setCandidateId(e.target.value)} required />
            </>
          )}
          <p>
            <button className="primary" type="submit">Continue</button>
          </p>
          {error && <p className="error">{error}</p>}
        </form>
      </div>
    </main>
  );
}
