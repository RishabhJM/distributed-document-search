"use client";

import React, { useState } from "react";
import Header from "@/components/Header";
import SearchTab from "@/components/SearchTab";
import IndexTab from "@/components/IndexTab";
import HealthTab from "@/components/HealthTab";
import DocModal, { DocDetail } from "@/components/DocModal";
import { Search, FilePlus, Activity } from "lucide-react";

export default function Home() {
  const [currentTenant, setCurrentTenant] = useState<string>("acme");
  const [activeTab, setActiveTab] = useState<"search" | "index" | "health">("search");
  const [selectedDoc, setSelectedDoc] = useState<DocDetail | null>(null);

  const handleTenantChange = (newTenant: string) => {
    setCurrentTenant(newTenant);
    // Set tenant cookie for proxy route handler
    document.cookie = `tenant_id=${newTenant}; path=/; max-age=86400`;
  };

  return (
    <div className="min-h-screen flex flex-col bg-slate-950 text-slate-100">
      {/* Top Header */}
      <Header
        currentTenant={currentTenant}
        onTenantChange={handleTenantChange}
      />

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 py-6 space-y-6">
        {/* Navigation Tabs */}
        <div className="flex items-center gap-2 border-b border-slate-800 pb-2">
          <button
            onClick={() => setActiveTab("search")}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition-all ${
              activeTab === "search"
                ? "bg-indigo-600 text-white shadow-md shadow-indigo-600/30"
                : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
            }`}
          >
            <Search className="w-3.5 h-3.5" />
            Search Documents
          </button>

          <button
            onClick={() => setActiveTab("index")}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition-all ${
              activeTab === "index"
                ? "bg-indigo-600 text-white shadow-md shadow-indigo-600/30"
                : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
            }`}
          >
            <FilePlus className="w-3.5 h-3.5" />
            Index Document
          </button>

          <button
            onClick={() => setActiveTab("health")}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-semibold transition-all ${
              activeTab === "health"
                ? "bg-indigo-600 text-white shadow-md shadow-indigo-600/30"
                : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
            }`}
          >
            <Activity className="w-3.5 h-3.5" />
            Topology &amp; Diagnostics
          </button>
        </div>

        {/* Tab Content */}
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
              // Can refresh or trigger update
            }}
          />
        )}

        {activeTab === "health" && (
          <HealthTab currentTenant={currentTenant} />
        )}
      </main>

      {/* Document Detail Modal */}
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
