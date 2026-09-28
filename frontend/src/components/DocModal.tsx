"use client";

import React, { useState } from "react";
import { X, Trash2, Calendar, User, Tag, FileText, CheckCircle, AlertTriangle } from "lucide-react";

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
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  if (!doc) return null;

  const handleDelete = async () => {
    if (!confirm(`Are you sure you want to delete "${doc.title}"?`)) return;
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
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-xl w-full max-w-2xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-slate-950/50">
          <div className="flex items-center gap-2">
            <span className="text-xs px-2 py-0.5 rounded font-mono bg-indigo-900/60 text-indigo-300 border border-indigo-700/50">
              {doc.tenantId}
            </span>
            <span className="text-xs text-slate-400 font-mono">
              ID: {doc.id}
            </span>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="p-6 overflow-y-auto space-y-4">
          <div>
            <h2 className="text-xl font-bold text-slate-100 mb-2">{doc.title}</h2>
            <div className="flex flex-wrap items-center gap-4 text-xs text-slate-400">
              {doc.author && (
                <span className="flex items-center gap-1">
                  <User className="w-3.5 h-3.5 text-slate-500" />
                  {doc.author}
                </span>
              )}
              <span className="flex items-center gap-1">
                <Calendar className="w-3.5 h-3.5 text-slate-500" />
                {new Date(doc.createdAt).toLocaleString()}
              </span>
              {doc.externalId && (
                <span className="flex items-center gap-1 font-mono text-slate-500">
                  ext: {doc.externalId}
                </span>
              )}
            </div>
          </div>

          {/* Tags */}
          {doc.tags && doc.tags.length > 0 && (
            <div className="flex items-center gap-1.5 flex-wrap">
              <Tag className="w-3.5 h-3.5 text-slate-500" />
              {doc.tags.map((t) => (
                <span
                  key={t}
                  className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700 font-mono"
                >
                  {t}
                </span>
              ))}
            </div>
          )}

          {/* Content */}
          <div className="bg-slate-950 border border-slate-800 rounded-lg p-4 font-sans text-sm text-slate-200 leading-relaxed whitespace-pre-wrap max-h-80 overflow-y-auto">
            {doc.content}
          </div>

          {deleteError && (
            <div className="p-3 rounded-lg bg-rose-950/50 border border-rose-800/80 text-rose-300 text-xs flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0 text-rose-400" />
              <span>{deleteError}</span>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-3 border-t border-slate-800 bg-slate-950/60">
          <div className="text-xs text-slate-500 font-mono">
            Version {doc.version ?? 0} · {doc.contentType || "text/plain"}
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={handleDelete}
              disabled={deleting}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-rose-950 text-rose-300 border border-rose-800 hover:bg-rose-900 transition-colors disabled:opacity-50"
            >
              <Trash2 className="w-3.5 h-3.5" />
              {deleting ? "Deleting..." : "Delete Document"}
            </button>
            <button
              onClick={onClose}
              className="px-4 py-1.5 rounded-lg text-xs font-semibold bg-slate-800 text-slate-200 hover:bg-slate-700 transition-colors"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
