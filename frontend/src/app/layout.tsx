import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Project Assessment",
  description: "Customized project assessments for candidates",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
