import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "DeepRunner · Distributed Document Search",
  description: "Enterprise Multi-Tenant Full-Text Search Platform with Shard Routing & Transactional Outbox",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="dark">
      <body className="min-h-screen bg-zinc-950 text-zinc-100 antialiased selection:bg-zinc-800 selection:text-zinc-100">
        {children}
      </body>
    </html>
  );
}
