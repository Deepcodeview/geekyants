"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { ErrorNote, Shell } from "@/components/Shell";
import { StatusBadge } from "@/components/StatusBadge";
import { usePoll } from "@/components/usePoll";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/format";
import type { AssignmentSummary, Page } from "@/lib/types";

export default function AdminHome() {
  const [page, setPage] = useState(0);
  const load = useCallback(() => api<Page<AssignmentSummary>>(`/api/v1/admin/assignments?page=${page}`), [page]);
  const { data, error } = usePoll(load, () => true, 10000);

  return (
    <Shell role="ADMIN">
      <h1>Assignments</h1>
      <p className="muted">Created automatically when the recruitment system sends a job and candidate.</p>
      <ErrorNote error={error} />
      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>Assignment</th>
              <th>Candidate</th>
              <th>Job</th>
              <th>Status</th>
              <th>Due</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((a) => (
              <tr key={a.assignmentId}>
                <td>
                  <Link href={`/admin/assignments/${a.assignmentId}`} className="mono">{a.assignmentId}</Link>
                </td>
                <td>
                  {a.candidateName}
                  <div className="muted small">{a.candidateId}</div>
                </td>
                <td>
                  {a.jobTitle}
                  <div className="muted small">{a.companyName}</div>
                </td>
                <td><StatusBadge status={a.status} /></td>
                <td>{formatDate(a.dueAt)}</td>
              </tr>
            ))}
            {data && data.content.length === 0 && (
              <tr><td colSpan={5} className="muted">No assignments yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {data && data.totalPages > 1 && (
        <div className="row">
          <button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span className="muted">Page {page + 1} of {data.totalPages}</span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </Shell>
  );
}
