"use client";

import Link from "next/link";
import { use, useCallback, useEffect, useState } from "react";
import { ErrorNote, Shell } from "@/components/Shell";
import { usePoll } from "@/components/usePoll";
import { api } from "@/lib/api";
import { humanize } from "@/lib/format";
import type { AssessmentAttempt } from "@/lib/types";

export default function AssessmentPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const base = `/api/v1/candidate/assignments/${id}/assessment`;
  const load = useCallback(() => api<AssessmentAttempt>(base), [base]);
  const { data, error, refresh, setError } = usePoll(load, () => false);
  const [answers, setAnswers] = useState<Record<string, number>>({});
  const remaining = useCountdown(data?.deadlineAt ?? null);

  useEffect(() => {
    if (!data) return;
    const initial: Record<string, number> = {};
    for (const q of data.questions) if (q.selectedOption !== null) initial[q.code] = q.selectedOption;
    setAnswers(initial);
  }, [data]);

  useEffect(() => {
    // Time is enforced by the server; reload so the page reflects the auto-submitted attempt.
    if (remaining === 0 && data?.status === "IN_PROGRESS") setTimeout(refresh, 2000);
  }, [remaining, data?.status, refresh]);

  async function run(fn: () => Promise<unknown>) {
    try {
      await fn();
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  async function choose(code: string, option: number) {
    setAnswers((a) => ({ ...a, [code]: option }));
    try {
      await api(`${base}/answers/${code}`, { method: "PUT", body: JSON.stringify({ selectedOption: option }) });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  return (
    <Shell role="CANDIDATE">
      <p><Link href={`/candidate/assignments/${id}`}>← Back to project</Link></p>
      <ErrorNote error={error} />
      {data?.status === "READY" && (
        <div className="card">
          <h1>Final assessment</h1>
          <p>The questions are about the project you submitted and the code you wrote. The timer starts when you click start and keeps running if you leave this page.</p>
          <button className="primary" onClick={() => run(() => api(`${base}/start`, { method: "POST" }))}>Start now</button>
        </div>
      )}
      {data?.status === "COMPLETED" && <div className="card"><h1>Assessment complete</h1><p>Your answers were recorded. Thank you.</p></div>}
      {data?.status === "IN_PROGRESS" && (
        <>
          <div className="card row spread" style={{ position: "sticky", top: 0, zIndex: 1 }}>
            <strong>{Object.keys(answers).length} of {data.questions.length} answered</strong>
            <span>Time left: <strong className="mono">{formatRemaining(remaining)}</strong></span>
          </div>
          {data.questions.map((q) => (
            <div className="card" key={q.code}>
              <div className="muted small">Question {q.position} · {humanize(q.category)}</div>
              <pre className="stem">{q.stem}</pre>
              {q.options.map((o, i) => (
                <label key={i} className={`option ${answers[q.code] === i ? "selected" : ""}`}>
                  <input type="radio" name={q.code} checked={answers[q.code] === i} onChange={() => choose(q.code, i)} />
                  <span>{o}</span>
                </label>
              ))}
            </div>
          ))}
          <button className="primary" onClick={() => {
            const unanswered = data.questions.length - Object.keys(answers).length;
            if (unanswered === 0 || confirm(`${unanswered} question(s) unanswered. Finish anyway?`)) {
              run(() => api(`${base}/finish`, { method: "POST" }));
            }
          }}>Finish assessment</button>
        </>
      )}
    </Shell>
  );
}

function useCountdown(deadline: string | null): number | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!deadline) return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [deadline]);
  if (!deadline) return null;
  return Math.max(0, Math.floor((new Date(deadline).getTime() - now) / 1000));
}

function formatRemaining(seconds: number | null): string {
  if (seconds === null) return "—";
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}
