"use client";

import Link from "next/link";
import { use, useCallback, useState } from "react";
import { ErrorNote, Shell } from "@/components/Shell";
import { StatusBadge } from "@/components/StatusBadge";
import { usePoll } from "@/components/usePoll";
import { api } from "@/lib/api";
import { formatDate, shortSha } from "@/lib/format";
import type { Dashboard } from "@/lib/types";

const STEPS = [
  ["IN_DEVELOPMENT", "Development"],
  ["EVALUATING", "Automated evaluation"],
  ["ASSESSMENT_READY", "Final assessment"],
  ["COMPLETED", "Completed"],
] as const;

export default function CandidateDashboard({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const load = useCallback(() => api<Dashboard>(`/api/v1/candidate/assignments/${id}`), [id]);
  const { data: d, error, refresh, setError } = usePoll(load,
    (d) => !!d && ["PREPARING", "IN_DEVELOPMENT", "EVALUATING"].includes(d.status), 15000);
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function submit() {
    setSubmitting(true);
    try {
      await api(`/api/v1/candidate/assignments/${id}/submit`, { method: "POST" });
      setConfirming(false);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSubmitting(false);
    }
  }

  const stepIndex = d ? STEPS.findIndex(([s]) => s === d.status || (s === "ASSESSMENT_READY" && d.status === "ASSESSMENT_IN_PROGRESS")) : -1;

  return (
    <Shell role="CANDIDATE">
      <p><Link href="/candidate">← My assignments</Link></p>
      <ErrorNote error={error} />
      {d && (
        <>
          <div className="row spread">
            <div>
              <h1>{d.project?.name ?? d.jobTitle}</h1>
              <p className="muted">{d.jobTitle} · {d.companyName}</p>
            </div>
            <StatusBadge status={d.status} />
          </div>

          {!d.project && d.status === "PREPARING" && <div className="card"><p>Your project is being prepared. We will let you know when it is ready.</p></div>}
          {d.status === "AWAITING_GITHUB_USERNAME" && <div className="card"><p>Link your GitHub account on the <Link href="/candidate">assignments page</Link> to get repository access.</p></div>}
          {d.status === "ON_HOLD" && <div className="card"><p>We hit a problem on our side and are looking into it. You do not need to do anything.</p></div>}

          {d.project && (
            <>
              <ul className="steps card">
                {STEPS.map(([s, label], i) => (
                  <li key={s} className={i < stepIndex ? "done" : i === stepIndex ? "current" : ""}>{label}</li>
                ))}
              </ul>

              <div className="grid2">
                <div className="card">
                  <h2>Project</h2>
                  <p>{d.project.technologies.join(" / ")}</p>
                  <p className="small muted">{d.project.durationDays} days · started {formatDate(d.project.assignedAt)} · due <strong>{formatDate(d.project.dueAt)}</strong></p>
                </div>
                {d.repository && (
                  <div className="card">
                    <h2>Repository</h2>
                    {d.repository.url && <p><a className="button primary" href={d.repository.url} target="_blank" rel="noreferrer">Open repository</a></p>}
                    {d.repository.cloneUrl && <p className="small"><code>git clone {d.repository.cloneUrl}</code></p>}
                    <p className="small muted">Access for {d.repository.githubUsername}: {d.repository.access === "READ" ? "read-only (submitted)" : "write"}</p>
                  </div>
                )}
              </div>

              <div className="card">
                <h2>What to build</h2>
                <p>{d.project.businessProblem}</p>
                <p style={{ whiteSpace: "pre-wrap" }}>{d.project.instructions}</p>
                {d.project.requirements.map((r) => (
                  <details key={r.code}>
                    <summary><span className="mono">{r.code}</span> {r.title} {!r.mandatory && <span className="badge muted">Optional</span>}</summary>
                    <p className="small">{r.description}</p>
                    <ul className="small">{r.acceptanceCriteria.map((c, i) => <li key={i}>{c}</li>)}</ul>
                  </details>
                ))}
                <p className="small muted">Full documentation is in the repository README and docs/ folder.</p>
              </div>

              <div className="card">
                <h2>Build feedback</h2>
                <p className="small muted">From the workflow in your repository. Informational only: the final evaluation runs separately on the commit you submit.</p>
                {d.ciRuns.length === 0 ? <p className="muted">No builds yet. Push to see results here.</p> : (
                  <div className="table-wrap">
                    <table>
                      <thead><tr><th>Commit</th><th>Result</th><th>Updated</th></tr></thead>
                      <tbody>
                        {d.ciRuns.map((r, i) => (
                          <tr key={i}>
                            <td className="mono">{shortSha(r.commitSha)}</td>
                            <td>{r.url ? <a href={r.url} target="_blank" rel="noreferrer"><StatusBadge status={(r.conclusion ?? r.status).toUpperCase() === "SUCCESS" ? "PASSED" : (r.conclusion ?? r.status).toUpperCase()} /></a> : r.status}</td>
                            <td className="small">{formatDate(r.updatedAt)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="card">
                <h2>Submission</h2>
                {d.submission ? (
                  <p>
                    Submitted commit <span className="mono">{shortSha(d.submission.commitSha)}</span> on {formatDate(d.submission.submittedAt)}
                    {d.submission.trigger === "DEADLINE" && " (automatically at the deadline)"}.
                  </p>
                ) : d.status === "IN_DEVELOPMENT" ? (
                  confirming ? (
                    <>
                      <p>We will record the latest commit on <code>main</code> and make the repository read-only. You cannot change it afterwards.</p>
                      <div className="row">
                        <button className="primary" disabled={submitting} onClick={submit}>{submitting ? "Submitting…" : "Yes, submit"}</button>
                        <button onClick={() => setConfirming(false)}>Cancel</button>
                      </div>
                    </>
                  ) : (
                    <>
                      <p className="muted">Push your work to <code>main</code>, then submit. Latest push: {d.recentPushes[0] ? `${shortSha(d.recentPushes[0].commitSha)} at ${formatDate(d.recentPushes[0].at)}` : "none yet"}.</p>
                      <button className="primary" onClick={() => setConfirming(true)}>Submit project</button>
                    </>
                  )
                ) : <p className="muted">Not submitted.</p>}
              </div>

              {d.assessment && (
                <div className="card">
                  <h2>Final assessment</h2>
                  {d.assessment.status === "COMPLETED" ? <p>Completed. Thank you. The team will be in touch.</p> : (
                    <>
                      <p>{d.assessment.questionCount} multiple-choice questions about your project and code. You have {d.assessment.timeLimitMinutes} minutes once you start.</p>
                      <Link className="button primary" href={`/candidate/assignments/${id}/assessment`}>
                        {d.assessment.status === "IN_PROGRESS" ? "Continue assessment" : "Start final assessment"}
                      </Link>
                    </>
                  )}
                </div>
              )}
            </>
          )}
        </>
      )}
    </Shell>
  );
}
