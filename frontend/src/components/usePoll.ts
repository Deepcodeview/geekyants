"use client";

import { useCallback, useEffect, useState } from "react";

/** Loads data and refreshes it while `active` is true (pipeline steps run asynchronously). */
export function usePoll<T>(load: () => Promise<T>, active: (data: T | null) => boolean, intervalMs = 3000) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setData(await load());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [load]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const polling = active(data);
  useEffect(() => {
    if (!polling) return;
    const id = setInterval(refresh, intervalMs);
    return () => clearInterval(id);
  }, [polling, refresh, intervalMs]);

  return { data, error, refresh, setError };
}
