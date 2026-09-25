"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { clearSession, getRole, type Role } from "@/lib/api";

/** Page chrome plus a client-side role gate. The backend enforces access; this only routes. */
export function Shell({ role, children }: { role: Role; children: ReactNode }) {
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (getRole() !== role) router.replace("/");
    else setReady(true);
  }, [role, router]);

  return (
    <>
      <header className="topbar">
        <Link href={role === "ADMIN" ? "/admin" : "/candidate"} className="brand">
          Project Assessment
        </Link>
        <span className="muted">{role === "ADMIN" ? "Admin" : "Candidate"}</span>
        <button
          className="link"
          onClick={() => {
            clearSession();
            router.replace("/");
          }}
        >
          Sign out
        </button>
      </header>
      <main className="container">{ready ? children : null}</main>
    </>
  );
}

export function ErrorNote({ error }: { error: string | null }) {
  return error ? <p className="error">{error}</p> : null;
}
