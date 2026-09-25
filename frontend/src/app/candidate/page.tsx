"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { ErrorNote, Shell } from "@/components/Shell";
import { StatusBadge } from "@/components/StatusBadge";
import { usePoll } from "@/components/usePoll";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/format";
import type { CandidateAssignment } from "@/lib/types";

export default function CandidateHome() {
  const load = useCallback(() => api<CandidateAssignment[]>("/api/v1/candidate/assignments"), []);
  const { data, error, refresh } = usePoll(load, (d) => !!d?.some((a) => a.status === "PREPARING"), 10000);
  const needsGithub = data?.some((a) => a.status === "AWAITING_GITHUB_USERNAME");

  return (
    <Shell role="CANDIDATE">
      <h1>My assignments</h1>
      <ErrorNote error={error} />
      {needsGithub && <GithubForm onLinked={refresh} />}
      {data?.map((a) => (
        <div className="card row spread" key={a.assignmentId}>
          <div>
            <strong>{a.jobTitle}</strong> · {a.companyName}
            <div className="muted small">Due {formatDate(a.dueAt)}</div>
          </div>
          <div className="row">
            <StatusBadge status={a.status} />
            <Link className="button" href={`/candidate/assignments/${a.assignmentId}`}>Open</Link>
          </div>
        </div>
      ))}
      {data?.length === 0 && <p className="muted">You have no assignments yet.</p>}
    </Shell>
  );
}

function GithubForm({ onLinked }: { onLinked: () => void }) {
  const [username, setUsername] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    try {
      await api("/api/v1/candidate/github-username", { method: "PUT", body: JSON.stringify({ username }) });
      setDone(true);
      onLinked();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  if (done) {
    return <div className="card"><p>Thanks. A repository invitation is on its way to <strong>{username}</strong> on GitHub. Accept it to get access.</p></div>;
  }
  return (
    <form className="card" onSubmit={submit}>
      <h2>Link your GitHub account</h2>
      <p className="muted">Your project repository is private. We invite this account to it, and only to it.</p>
      <label htmlFor="gh">GitHub username</label>
      <input id="gh" value={username} onChange={(e) => setUsername(e.target.value)} required />
      <p><button className="primary" type="submit">Link account</button></p>
      <ErrorNote error={error} />
    </form>
  );
}
