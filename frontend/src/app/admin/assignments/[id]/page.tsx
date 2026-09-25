"use client";

import Link from "next/link";
import { use, useCallback, useState } from "react";
import { ErrorNote, Shell } from "@/components/Shell";
import { StatusBadge } from "@/components/StatusBadge";
import { usePoll } from "@/components/usePoll";
import { api } from "@/lib/api";
import { formatDate, shortSha } from "@/lib/format";
import type { AssignmentDetail, AuditEvent, Blueprint, BlueprintContent, ProjectConfiguration } from "@/lib/types";

/** States in which a background step is running; the page refreshes until they change. */
const BUSY = new Set([
  "ANALYZING_REQUIREMENTS", "BLUEPRINT_GENERATING", "PROVISIONING", "SUBMITTED", "EVALUATING",
  "ANALYZING_CODE", "GENERATING_MCQ", "MCQ_COMPLETED",
]);
const CONFIGURABLE = new Set(["ANALYZED", "BLUEPRINT_DRAFT"]);
const REPORTABLE = new Set(["REPORTED"]);

type Data = { detail: AssignmentDetail; blueprints: Blueprint[]; audit: AuditEvent[] };

export default function AssignmentPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const load = useCallback(async (): Promise<Data> => {
    const [detail, blueprints, audit] = await Promise.all([
      api<AssignmentDetail>(`/api/v1/admin/assignments/${id}`),
      api<Blueprint[]>(`/api/v1/admin/assignments/${id}/blueprints`),
      api<AuditEvent[]>(`/api/v1/admin/assignments/${id}/audit`),
    ]);
    return { detail, blueprints, audit };
  }, [id]);
  const { data, error, refresh, setError } = usePoll(load, (d) => !!d && BUSY.has(d.detail.summary.status));

  async function act(fn: () => Promise<unknown>) {
    try {
      await fn();
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  if (!data) {
    return <Shell role="ADMIN"><ErrorNote error={error} />{!error && <p className="muted">Loading…</p>}</Shell>;
  }
  const { detail, blueprints, audit } = data;
  const status = detail.summary.status;
  const draft = blueprints.find((b) => b.status === "DRAFT");
  const approved = blueprints.find((b) => b.status === "APPROVED");

  return (
    <Shell role="ADMIN">
      <div className="row spread">
        <div>
          <h1 className="mono">{id}</h1>
          <p className="muted">
            {detail.summary.candidateName} ({detail.summary.candidateId}) · {detail.summary.jobTitle} ·{" "}
            {detail.summary.companyName}
          </p>
        </div>
        <div className="row">
          <StatusBadge status={status} />
          {REPORTABLE.has(status) && <Link className="button primary" href={`/admin/assignments/${id}/report`}>View report</Link>}
        </div>
      </div>
      <ErrorNote error={error} />

      {status === "FAILED" && (
        <div className="card">
          <h2>Step failed</h2>
          <p className="error">{detail.failureReason}</p>
          <button className="primary" onClick={() => act(() => api(`/api/v1/admin/assignments/${id}/retry`, { method: "POST" }))}>
            Retry step
          </button>
        </div>
      )}
      {BUSY.has(status) && (
        <p className="muted">
          Working in the background… this page refreshes automatically.{" "}
          <button className="link" title="Available once the step has made no progress for 15 minutes"
            onClick={() => act(() => api(`/api/v1/admin/assignments/${id}/retry`, { method: "POST" }))}>
            Stuck? Retry
          </button>
        </p>
      )}

      {detail.requirementAnalysis && <AnalysisCard detail={detail} onRerun={status === "ANALYZED"
        ? () => act(() => api(`/api/v1/admin/assignments/${id}/analysis/rerun`, { method: "POST" }))
        : undefined} />}

      {CONFIGURABLE.has(status) && (
        <ConfigurationCard
          initial={detail.projectConfiguration ?? defaultConfig(detail)}
          onSave={(config) => act(() => api(`/api/v1/admin/assignments/${id}/configuration`, {
            method: "PUT", body: JSON.stringify(config),
          }))}
          configured={!!detail.projectConfiguration}
          hasDraft={!!draft}
          onGenerate={(instructions) => act(() => api(`/api/v1/admin/assignments/${id}/blueprints/generate`, {
            method: "POST", body: JSON.stringify({ instructions }),
          }))}
        />
      )}

      {(draft ?? approved) && (
        <BlueprintCard
          blueprint={(draft ?? approved)!}
          editable={status === "BLUEPRINT_DRAFT" && !!draft}
          onApprove={() => act(() => api(`/api/v1/admin/assignments/${id}/blueprints/${draft!.blueprintId}/approve`, { method: "POST" }))}
          onSave={(content) => act(() => api(`/api/v1/admin/assignments/${id}/blueprints/draft`, {
            method: "PUT", body: JSON.stringify(content),
          }))}
        />
      )}

      {detail.repository && (
        <div className="card">
          <h2>Repository and submissions</h2>
          <p>
            {detail.repository.url ? <a href={detail.repository.url} target="_blank" rel="noreferrer">{detail.repository.fullName}</a>
              : detail.repository.fullName}{" "}
            <StatusBadge status={detail.repository.status} />{" "}
            <span className="muted small">candidate access: {detail.repository.candidateAccess} · GitHub: {detail.githubUsername ?? "not linked"}</span>
          </p>
          <p className="muted small">Assigned {formatDate(detail.assignedAt)} · due {formatDate(detail.summary.dueAt)}</p>
          {detail.submissions.length > 0 && (
            <div className="table-wrap">
              <table>
                <thead><tr><th>Submission</th><th>Commit</th><th>Tag</th><th>By</th><th>At</th></tr></thead>
                <tbody>
                  {detail.submissions.map((s) => (
                    <tr key={s.submissionId}>
                      <td className="mono">{s.submissionId}</td>
                      <td className="mono">{shortSha(s.commitSha)}</td>
                      <td className="mono small">{s.tagRef}</td>
                      <td>{s.trigger}</td>
                      <td>{formatDate(s.submittedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      <div className="card">
        <h2>Audit trail</h2>
        <div className="table-wrap">
          <table>
            <thead><tr><th>When</th><th>Event</th><th>Actor</th><th>Detail</th></tr></thead>
            <tbody>
              {audit.map((e, i) => (
                <tr key={i}>
                  <td className="small">{formatDate(e.at)}</td>
                  <td className="mono small">{e.type}</td>
                  <td className="small">{e.actor}</td>
                  <td className="small">{e.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </Shell>
  );
}

function defaultConfig(detail: AssignmentDetail): ProjectConfiguration {
  const a = detail.requirementAnalysis;
  return {
    domain: a?.domain ?? "",
    projectType: a?.projectIdeas?.[0]?.title ?? "",
    complexity: a?.recommendedComplexity ?? "INTERMEDIATE",
    durationDays: detail.durationDays ?? 7,
    technologies: a?.recommendedTechnologies ?? ["Java", "Spring Boot"],
    mandatoryRequirements: a?.requiredCapabilities ?? [],
    optionalRequirements: [],
    notes: "",
  };
}

function AnalysisCard({ detail, onRerun }: { detail: AssignmentDetail; onRerun?: () => void }) {
  const a = detail.requirementAnalysis!;
  return (
    <div className="card">
      <div className="row spread">
        <h2>Requirement analysis</h2>
        {onRerun && <button onClick={onRerun}>Re-run analysis</button>}
      </div>
      <p>{a.summary}</p>
      <p className="small muted">
        Domain: {a.domain} · Recommended complexity: {a.recommendedComplexity} · Gaps:{" "}
        {a.skillGaps.length ? a.skillGaps.join(", ") : "none"}
      </p>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Skill</th><th>Required</th><th>Needed</th><th>Candidate</th><th>Evidence</th></tr></thead>
          <tbody>
            {a.skills.map((s) => (
              <tr key={s.skill}>
                <td>{s.skill}</td>
                <td>{s.requiredByJob ? "Yes" : "—"}</td>
                <td>{s.requiredLevel}</td>
                <td>{s.candidateLevel}</td>
                <td className="small muted">{s.evidence}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

const lines = (s: string) => s.split("\n").map((x) => x.trim()).filter(Boolean);

function ConfigurationCard(props: {
  initial: ProjectConfiguration;
  configured: boolean;
  hasDraft: boolean;
  onSave: (c: ProjectConfiguration) => void;
  onGenerate: (instructions: string) => void;
}) {
  const [c, setC] = useState(props.initial);
  const [mandatory, setMandatory] = useState(props.initial.mandatoryRequirements.join("\n"));
  const [optional, setOptional] = useState(props.initial.optionalRequirements.join("\n"));
  const [instructions, setInstructions] = useState("");

  return (
    <div className="card">
      <h2>Project configuration</h2>
      <div className="grid2">
        <div>
          <label htmlFor="domain">Domain</label>
          <input id="domain" value={c.domain} onChange={(e) => setC({ ...c, domain: e.target.value })} />
          <label htmlFor="type">Project type</label>
          <input id="type" value={c.projectType} onChange={(e) => setC({ ...c, projectType: e.target.value })} />
          <label htmlFor="complexity">Complexity</label>
          <select id="complexity" value={c.complexity}
            onChange={(e) => setC({ ...c, complexity: e.target.value as ProjectConfiguration["complexity"] })}>
            <option>BASIC</option><option>INTERMEDIATE</option><option>ADVANCED</option>
          </select>
          <label htmlFor="duration">Duration (days)</label>
          <input id="duration" type="number" min={1} max={30} value={c.durationDays}
            onChange={(e) => setC({ ...c, durationDays: Number(e.target.value) })} />
        </div>
        <div>
          <label htmlFor="tech">Technologies (comma separated)</label>
          <input id="tech" value={c.technologies.join(", ")}
            onChange={(e) => setC({ ...c, technologies: e.target.value.split(",").map((t) => t.trim()).filter(Boolean) })} />
          <label htmlFor="mandatory">Mandatory requirements (one per line)</label>
          <textarea id="mandatory" value={mandatory} onChange={(e) => setMandatory(e.target.value)} />
          <label htmlFor="optional">Optional requirements (one per line)</label>
          <textarea id="optional" value={optional} onChange={(e) => setOptional(e.target.value)} />
        </div>
      </div>
      <p>
        <button onClick={() => props.onSave({ ...c, mandatoryRequirements: lines(mandatory), optionalRequirements: lines(optional) })}>
          Save configuration
        </button>
      </p>
      {props.configured && (
        <>
          <label htmlFor="instructions">{props.hasDraft ? "What should change in the draft?" : "Extra instructions (optional)"}</label>
          <textarea id="instructions" value={instructions} onChange={(e) => setInstructions(e.target.value)} />
          <p>
            <button className="primary" onClick={() => props.onGenerate(instructions)}>
              {props.hasDraft ? "Regenerate blueprint" : "Generate blueprint"}
            </button>
          </p>
        </>
      )}
    </div>
  );
}

function BlueprintCard(props: {
  blueprint: Blueprint;
  editable: boolean;
  onApprove: () => void;
  onSave: (content: BlueprintContent) => void;
}) {
  const b = props.blueprint;
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState("");
  const [parseError, setParseError] = useState<string | null>(null);

  return (
    <div className="card">
      <div className="row spread">
        <h2>
          Blueprint <span className="mono">{b.blueprintId}</span> v{b.version} <StatusBadge status={b.status} />
        </h2>
        {props.editable && (
          <div className="row">
            <button onClick={() => { setText(JSON.stringify(b.content, null, 2)); setEditing(!editing); }}>
              {editing ? "Cancel edit" : "Edit"}
            </button>
            <button className="primary" onClick={props.onApprove}>Approve and create repository</button>
          </div>
        )}
      </div>
      {b.approvedBy && <p className="muted small">Approved by {b.approvedBy} on {formatDate(b.approvedAt)}</p>}

      {editing ? (
        <>
          <p className="muted small">Edit the blueprint JSON. Saving creates a new version.</p>
          <textarea className="mono" style={{ minHeight: 400 }} value={text} onChange={(e) => setText(e.target.value)} />
          {parseError && <p className="error">{parseError}</p>}
          <p>
            <button className="primary" onClick={() => {
              try {
                props.onSave(JSON.parse(text));
                setEditing(false);
                setParseError(null);
              } catch (e) {
                setParseError(e instanceof Error ? e.message : String(e));
              }
            }}>Save as new version</button>
          </p>
        </>
      ) : (
        <>
          <h3>{b.content.projectName}</h3>
          <p>{b.content.businessProblem}</p>
          <p className="muted">{b.content.scope}</p>
          <div className="table-wrap">
            <table>
              <thead><tr><th>ID</th><th>Requirement</th><th>Category</th><th>Acceptance criteria</th></tr></thead>
              <tbody>
                {b.content.requirements.map((r) => (
                  <tr key={r.code}>
                    <td className="mono">{r.code}</td>
                    <td>
                      <strong>{r.title}</strong> {!r.mandatory && <span className="badge muted">Optional</span>}
                      <div className="small muted">{r.description}</div>
                    </td>
                    <td className="small">{r.category}</td>
                    <td className="small"><ul>{r.acceptanceCriteria.map((c, i) => <li key={i}>{c}</li>)}</ul></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {b.content.apis.length > 0 && (
            <>
              <h3>APIs</h3>
              <ul className="small">
                {b.content.apis.map((a, i) => (
                  <li key={i}><code>{a.method} {a.path}</code> — {a.description}</li>
                ))}
              </ul>
            </>
          )}
          {b.content.events.length > 0 && (
            <>
              <h3>Events</h3>
              <ul className="small">
                {b.content.events.map((e, i) => <li key={i}><code>{e.topic}</code> — {e.name}</li>)}
              </ul>
            </>
          )}
        </>
      )}
    </div>
  );
}
