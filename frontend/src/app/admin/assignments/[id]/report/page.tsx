"use client";

import Link from "next/link";
import { use, useCallback } from "react";
import { ErrorNote, Shell } from "@/components/Shell";
import { StatusBadge } from "@/components/StatusBadge";
import { usePoll } from "@/components/usePoll";
import { api } from "@/lib/api";
import { formatDate, humanize, shortSha } from "@/lib/format";
import type { Report } from "@/lib/types";

const LETTERS = ["A", "B", "C", "D"];

export default function ReportPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const load = useCallback(() => api<Report>(`/api/v1/admin/assignments/${id}/report`), [id]);
  const { data: r, error } = usePoll(load, () => false);

  return (
    <Shell role="ADMIN">
      <p><Link href={`/admin/assignments/${id}`}>← {id}</Link></p>
      <ErrorNote error={error} />
      {r && (
        <>
          <h1>Assessment report</h1>
          <p className="muted">
            {r.candidate.name} ({r.candidate.candidateId}) · {r.candidate.jobTitle} · {r.candidate.companyName} ·{" "}
            <span className="mono">{r.reportId}</span> · generated {formatDate(r.generatedAt)}
          </p>

          <div className="grid2">
            {(["overall", "requirements", "automatedEvaluation", "codeQuality", "mcq"] as const).map((k) => (
              <div className="card" key={k}>
                <div className="muted small">{k === "overall" ? "Overall (weighted)" : humanize(k.replace(/([A-Z])/g, "_$1"))}
                  {k !== "overall" && r.scores.weights[k] !== undefined && ` · weight ${r.scores.weights[k]}%`}</div>
                <div className="stat">{r.scores[k] ?? "—"}{r.scores[k] !== null && "%"}</div>
              </div>
            ))}
          </div>
          <p className="muted small">Scores support the review. The hiring decision stays with the reviewers.</p>

          <div className="card">
            <h2>Project and submission</h2>
            <p>
              <strong>{r.project.name}</strong> · {r.project.domain} · {r.project.complexity} · {r.project.technologies.join(", ")}
            </p>
            <p className="small">
              <a href={r.repository.url} target="_blank" rel="noreferrer">{r.repository.fullName}</a> · submission{" "}
              <span className="mono">{r.repository.submissionId}</span> · commit{" "}
              <span className="mono">{shortSha(r.repository.commitSha)}</span> ({r.repository.tagRef}) · {r.repository.submittedBy}{" "}
              on {formatDate(r.repository.submittedAt)}
            </p>
            <p className="small muted">
              Project <span className="mono">{r.project.projectId}</span> · blueprint <span className="mono">{r.project.blueprintId}</span>
            </p>
          </div>

          {r.automatedEvaluation && (
            <div className="card">
              <h2>Automated evaluation</h2>
              <div className="row">
                {r.automatedEvaluation.stages.map((s) => (
                  <span key={s.name} title={s.summary ?? ""}>{humanize(s.name)} <StatusBadge status={s.outcome} /></span>
                ))}
              </div>
              <p className="small">
                Visible tests: {r.automatedEvaluation.visibleTests ? `${r.automatedEvaluation.visibleTests.passed}/${r.automatedEvaluation.visibleTests.total}` : "—"} ·
                Hidden tests: {r.automatedEvaluation.hiddenTests ? `${r.automatedEvaluation.hiddenTests.passed}/${r.automatedEvaluation.hiddenTests.total}` : "—"}
                {r.automatedEvaluation.pipelineUrl && <> · <a href={r.automatedEvaluation.pipelineUrl} target="_blank" rel="noreferrer">pipeline run</a></>}
              </p>
              {!!r.automatedEvaluation.failedTests?.length && (
                <p className="small muted">Failed: {r.automatedEvaluation.failedTests.join(", ")}</p>
              )}
            </div>
          )}

          <div className="card table-wrap">
            <h2>Requirements</h2>
            <table>
              <thead><tr><th>ID</th><th>Requirement</th><th>Status</th><th>Implementation</th><th>Tests</th></tr></thead>
              <tbody>
                {r.requirements.map((q) => (
                  <tr key={q.code}>
                    <td className="mono">{q.code}</td>
                    <td>{q.title}{!q.mandatory && <span className="muted small"> (optional)</span>}
                      {q.notes && <div className="small muted">{q.notes}</div>}</td>
                    <td><StatusBadge status={q.status} /></td>
                    <td className="mono small">{q.implementationRefs.join("\n") || "—"}</td>
                    <td className="mono small">{q.testRefs.join("\n") || "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {r.codeQuality && (
            <div className="card">
              <h2>Code analysis</h2>
              <p>{r.codeQuality.summary}</p>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Area</th><th>Score</th><th>Strengths</th><th>Issues</th></tr></thead>
                  <tbody>
                    {r.codeQuality.areas.map((a) => (
                      <tr key={a.area}>
                        <td>{a.area}</td><td>{a.score}/10</td>
                        <td className="small">{a.strengths.join("; ")}</td>
                        <td className="small">{a.issues.join("; ")}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {r.codeQuality.findings.length > 0 && (
                <>
                  <h3>Findings</h3>
                  <ul className="small">
                    {r.codeQuality.findings.map((f, i) => (
                      <li key={i}><StatusBadge status={f.severity} /> <code>{f.file}{f.symbol ? `#${f.symbol}` : ""}</code> — {f.description}
                        {f.recommendation && <span className="muted"> Fix: {f.recommendation}</span>}</li>
                    ))}
                  </ul>
                </>
              )}
              <p className="small muted">
                {r.codeQuality.filesAnalyzed} files analysed
                {r.codeQuality.filesSkipped.length > 0 && `, ${r.codeQuality.filesSkipped.length} skipped (${
                  [...new Set(r.codeQuality.filesSkipped.map((f) => f.reason))].join(", ")})`}
              </p>
            </div>
          )}

          <div className="card">
            <h2>MCQ: {r.mcq.correct}/{r.mcq.total}</h2>
            <p className="small muted">
              {Object.entries(r.mcq.byCategory).map(([c, s]) => `${humanize(c)} ${s.correct}/${s.total}`).join(" · ")}
            </p>
            {r.mcq.questions.map((q) => (
              <details key={q.code} style={{ marginBottom: 8 }}>
                <summary>
                  <span className="mono">{q.code}</span> <StatusBadge status={q.correct ? "PASSED" : "FAILED"} /> {humanize(q.category)}
                  {q.sourceRef && <span className="mono small muted"> · {q.sourceRef}</span>}
                </summary>
                <pre className="stem">{q.stem}</pre>
                <ol type="A" className="small">
                  {q.options.map((o, i) => (
                    <li key={i}>
                      {o} {i === q.correctOption && <strong>(correct)</strong>}
                      {i === q.selectedOption && i !== q.correctOption && <strong> (selected)</strong>}
                    </li>
                  ))}
                </ol>
                <p className="small muted">
                  Selected: {q.selectedOption === null ? "no answer" : LETTERS[q.selectedOption]} · {q.explanation}
                  {q.requirementCode && ` · ${q.requirementCode}`}
                </p>
              </details>
            ))}
          </div>
        </>
      )}
    </Shell>
  );
}
