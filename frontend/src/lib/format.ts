export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  return new Date(value).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export function shortSha(sha: string | null | undefined): string {
  return sha ? sha.slice(0, 8) : "—";
}

export function humanize(value: string): string {
  return value.toLowerCase().replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase());
}
