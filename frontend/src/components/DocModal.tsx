"use client";

import React, { useState } from "react";
import { X, Trash2, Calendar, User, Tag, FileText, Check, Copy, Code, Layers } from "lucide-react";

export interface DocDetail {
  id: string;
  tenantId: string;
  externalId?: string;
  title: string;
  content: string;
  author?: string;
  tags?: string[];
  contentType?: string;
  version?: number;
  indexingState?: string;
  createdAt: string;
  updatedAt: string;
}

interface DocModalProps {
  doc: DocDetail | null;
  onClose: () => void;
  onDeleted: (id: string) => void;
}

export default function DocModal({ doc, onClose, onDeleted }: DocModalProps) {
  const [activeTab, setActiveTab] = useState<"formatted" | "json" | "partitioning">("formatted");
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  if (!doc) return null;

  const handleCopyId = () => {
    navigator.clipboard.writeText(doc.id);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDelete = async () => {
    if (!confirm(`Are you sure you want to soft-delete "${doc.title}"? This records an outbox de-index event.`)) return;
    setDeleting(true);
    setDeleteError(null);

    try {
      const res = await fetch(`/api/documents/${doc.id}`, {
        method: "DELETE",
      });

      if (res.ok || res.status === 204) {
        onDeleted(doc.id);
        onClose();
      } else {
        const errorData = await res.json();
        setDeleteError(errorData.detail || "Failed to delete document");
      }
    } catch (e: any) {
      setDeleteError(e.message || "Network error");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-zinc-900 border border-zinc-800 rounded-xl w-full max-w-2xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Modal Header */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-zinc-800/80 bg-zinc-950/60">
          <div className="flex items-center gap-2.5 min-w-0">
            <span className="text-[11px] font-mono px-2 py-0.5 rounded font-medium bg-zinc-800 text-zinc-300 border border-zinc-700/60 shrink-0">
              {doc.tenantId}
            </span>
            <span className="text-xs font-mono text-zinc-400 truncate">
              {doc.id}
            </span>
            <button
              onClick={handleCopyId}
              className="text-zinc-500 hover:text-zinc-300 transition-colors p-1"
              title="Copy UUID"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
            </button>
          </div>

          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-zinc-200 transition-colors p-1.5 rounded-lg hover:bg-zinc-800"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Modal View Selector Tabs */}
        <div className="flex items-center gap-1 px-5 pt-3 border-b border-zinc-800/60 text-xs bg-zinc-950/30">
          <button
            onClick={() => setActiveTab("formatted")}
            className={`pb-2.5 px-2 border-b-2 font-medium transition-colors ${
              activeTab === "formatted"
                ? "border-zinc-200 text-zinc-100"
                : "border-transparent text-zinc-400 hover:text-zinc-300"
            }`}
          >
            Document Content
          </button>
          <button
            onClick={() => setActiveTab("json")}
            className={`pb-2.5 px-2 border-b-2 font-medium transition-colors ${
              activeTab === "json"
                ? "border-zinc-200 text-zinc-100"
                : "border-transparent text-zinc-400 hover:text-zinc-300"
            }`}
          >
            Raw Storage JSON
          </button>
          <button
            onClick={() => setActiveTab("partitioning")}
            className={`pb-2.5 px-2 border-b-2 font-medium transition-colors ${
              activeTab === "partitioning"
                ? "border-zinc-200 text-zinc-100"
                : "border-transparent text-zinc-400 hover:text-zinc-300"
            }`}
          >
            Partitioning &amp; Routing
          </button>
        </div>

        {/* Modal Body */}
        <div className="flex-1 overflow-y-auto p-5 space-y-4">
          {activeTab === "formatted" && (
            <div className="space-y-4">
              <div>
                <h2 className="text-base font-semibold text-zinc-100 tracking-tight">
                  {doc.title}
                </h2>
                {doc.externalId && (
                  <span className="font-mono text-[11px] text-zinc-400 mt-1 inline-block">
                    external_id: {doc.externalId}
                  </span>
                )}
              </div>

              {/* Tag Chips */}
              {doc.tags && doc.tags.length > 0 && (
                <div className="flex items-center gap-1.5 flex-wrap">
                  {doc.tags.map((t) => (
                    <span
                      key={t}
                      className="font-mono text-[10px] text-zinc-300 bg-zinc-950 px-2 py-0.5 rounded border border-zinc-800"
                    >
                      #{t}
                    </span>
                  ))}
                </div>
              )}

              {/* Text Body */}
              <div className="p-4 rounded-xl bg-zinc-950/70 border border-zinc-800/80 text-xs text-zinc-300 whitespace-pre-wrap font-mono leading-relaxed max-h-72 overflow-y-auto">
                {doc.content}
              </div>

              {/* Author & Timestamp Footer */}
              <div className="flex items-center justify-between text-[11px] text-zinc-400 pt-2 border-t border-zinc-800/60 font-mono">
                <span>author: {doc.author || "system"}</span>
                <span>created: {new Date(doc.createdAt).toLocaleString()}</span>
              </div>
            </div>
          )}

          {activeTab === "json" && (
            <div className="space-y-2">
              <div className="text-[11px] text-zinc-400 font-mono">
                ACID PostgreSQL row &amp; OpenSearch index payload representation:
              </div>
              <pre className="p-4 rounded-xl bg-zinc-950/80 border border-zinc-800/80 text-[11px] font-mono text-zinc-300 overflow-x-auto max-h-80 leading-relaxed">
                {JSON.stringify(doc, null, 2)}
              </pre>
            </div>
          )}

          {activeTab === "partitioning" && (
            <div className="space-y-3 text-xs">
              <div className="p-3 rounded-lg bg-zinc-950/60 border border-zinc-800/80 space-y-2">
                <div className="font-semibold text-zinc-200">OpenSearch Shard Confinement</div>
                <div className="text-[11px] text-zinc-400 font-mono space-y-1">
                  <div>Index: <span className="text-zinc-200">documents-v1 (alias: documents-live)</span></div>
                  <div>Document ID: <span className="text-zinc-200">{doc.tenantId}:{doc.id}</span></div>
                  <div>Routing Parameter: <span className="text-emerald-400">?routing={doc.tenantId}</span></div>
                  <div>Shard Allocation: <span className="text-zinc-200">Confined to exactly 1 primary shard</span></div>
                </div>
              </div>

              <div className="p-3 rounded-lg bg-zinc-950/60 border border-zinc-800/80 space-y-2">
                <div className="font-semibold text-zinc-200">PostgreSQL ACID Isolation</div>
                <div className="text-[11px] text-zinc-400 font-mono space-y-1">
                  <div>Table: <span className="text-zinc-200">public.documents</span></div>
                  <div>Query Filter: <span className="text-zinc-200">WHERE tenant_id = &apos;{doc.tenantId}&apos; AND deleted_at IS NULL</span></div>
                  <div>ArchUnit Rule: <span className="text-emerald-400">Untenanted findById strictly prohibited</span></div>
                </div>
              </div>
            </div>
          )}

          {deleteError && (
            <div className="p-3 rounded-lg bg-rose-950/40 border border-rose-800/60 text-rose-300 text-xs">
              {deleteError}
            </div>
          )}
        </div>

        {/* Modal Action Footer */}
        <div className="flex items-center justify-between px-5 py-3.5 border-t border-zinc-800/80 bg-zinc-950/60">
          <div className="text-[11px] text-zinc-400 font-mono">
            Status: {doc.indexingState || "ACTIVE"}
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleDelete}
              disabled={deleting}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-rose-400 hover:text-rose-300 hover:bg-rose-950/40 border border-rose-900/60 transition-colors disabled:opacity-50 cursor-pointer"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>{deleting ? "Purging..." : "Soft Delete"}</span>
            </button>
            <button
              onClick={onClose}
              className="px-3 py-1.5 rounded-lg text-xs font-medium text-zinc-300 hover:text-white bg-zinc-800 hover:bg-zinc-700 transition-colors cursor-pointer"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
