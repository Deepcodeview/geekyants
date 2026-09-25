import { humanize } from "@/lib/format";

const TONE: Record<string, string> = {
  FAILED: "bad",
  ON_HOLD: "bad",
  REPORTED: "good",
  COMPLETED: "good",
  APPROVED: "good",
  PASSED: "good",
  IMPLEMENTED: "good",
  PARTIALLY_IMPLEMENTED: "warn",
  NOT_IMPLEMENTED: "bad",
  NOT_ASSESSED: "muted",
  SKIPPED: "muted",
  SUPERSEDED: "muted",
  ERROR: "bad",
  BLUEPRINT_DRAFT: "warn",
  AWAITING_GITHUB_USERNAME: "warn",
  MCQ_READY: "warn",
  ASSESSMENT_READY: "warn",
};

export function StatusBadge({ status }: { status: string }) {
  return <span className={`badge ${TONE[status] ?? "info"}`}>{humanize(status)}</span>;
}
