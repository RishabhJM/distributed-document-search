"use client";

import React, { useState, useEffect } from "react";
import Header from "@/components/Header";
import SearchTab from "@/components/SearchTab";
import IndexTab from "@/components/IndexTab";
import HealthTab from "@/components/HealthTab";
import DocModal, { DocDetail } from "@/components/DocModal";
import { Search, FilePlus, BarChart3 } from "lucide-react";

export default function Home() {
  const [currentTenant, setCurrentTenant] = useState<string>("acme");
  const [activeTab, setActiveTab] = useState<"search" | "index" | "metrics">("search");
  const [selectedDoc, setSelectedDoc] = useState<DocDetail | null>(null);

  const handleTenantChange = (newTenant: string) => {
    setCurrentTenant(newTenant);
    // Persist cookie for Next.js route proxy
    document.cookie = `tenant_id=${newTenant}; path=/; max-age=86400`;
  };

  // Keyboard shortcut: Cmd+K or / switches to search tab
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        setActiveTab("search");
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  return (
    <div className="min-h-screen flex flex-col bg-zinc-950 text-zinc-100">
      {/* Top Navbar */}
      <Header
        currentTenant={currentTenant}
        onTenantChange={handleTenantChange}
      />

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 py-6 space-y-6">
        {/* Floating Minimalist Segmented Tab Bar */}
        <div className="flex items-center justify-between border-b border-zinc-800/80 pb-3">
          <div className="flex items-center p-1 rounded-xl bg-zinc-900/70 border border-zinc-800/80 shadow-sm">
            <button
              onClick={() => setActiveTab("search")}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
                activeTab === "search"
                  ? "bg-zinc-800 text-zinc-100 shadow-sm border border-zinc-700/60"
                  : "text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/40"
              }`}
            >
              <Search className="w-3.5 h-3.5" />
              <span>Search Retrieval</span>
              <kbd className="hidden sm:inline-block font-mono text-[10px] text-zinc-500 bg-zinc-900/80 px-1.5 py-0.5 rounded border border-zinc-700/50">
                ⌘K
              </kbd>
            </button>

            <button
              onClick={() => setActiveTab("index")}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
                activeTab === "index"
                  ? "bg-zinc-800 text-zinc-100 shadow-sm border border-zinc-700/60"
                  : "text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/40"
              }`}
            >
              <FilePlus className="w-3.5 h-3.5" />
              <span>Document Studio</span>
            </button>

            <button
              onClick={() => setActiveTab("metrics")}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
                activeTab === "metrics"
                  ? "bg-zinc-800 text-zinc-100 shadow-sm border border-zinc-700/60"
                  : "text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/40"
              }`}
            >
              <BarChart3 className="w-3.5 h-3.5" />
              <span>Metrics &amp; Tradeoffs</span>
            </button>
          </div>

          {/* Quick Context Indicator */}
          <div className="hidden sm:flex items-center gap-2 text-[11px] text-zinc-500 font-mono">
            <span>Partitioned on</span>
            <span className="text-zinc-300 bg-zinc-900 px-2 py-0.5 rounded border border-zinc-800 font-medium">
              routing={currentTenant}
            </span>
          </div>
        </div>

        {/* Tab Viewport */}
        {activeTab === "search" && (
          <SearchTab
            currentTenant={currentTenant}
            onSelectDoc={(doc) => setSelectedDoc(doc)}
          />
        )}

        {activeTab === "index" && (
          <IndexTab
            currentTenant={currentTenant}
            onDocumentCreated={() => {
              // Optionally switch to search or notify
            }}
          />
        )}

        {activeTab === "metrics" && (
          <HealthTab currentTenant={currentTenant} />
        )}
      </main>

      {/* Minimalist Document Detail Drawer/Modal */}
      <DocModal
        doc={selectedDoc}
        onClose={() => setSelectedDoc(null)}
        onDeleted={() => {
          setSelectedDoc(null);
        }}
      />
    </div>
  );
}
