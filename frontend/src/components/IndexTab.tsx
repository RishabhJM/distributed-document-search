"use client";

import React, { useState } from "react";
import { Send, CheckCircle2, Clock, AlertCircle, FilePlus, Copy } from "lucide-react";

interface IndexTabProps {
  currentTenant: string;
  onDocumentCreated: () => void;
}

export default function IndexTab({ currentTenant, onDocumentCreated }: IndexTabProps) {
  const [title, setTitle] = useState("");
  const [author, setAuthor] = useState("");
  const [externalId, setExternalId] = useState("");
  const [tags, setTags] = useState("");
  const [contentType, setContentType] = useState("text/plain");
  const [content, setContent] = useState("");

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<any | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !content.trim()) return;

    setLoading(true);
    setError(null);
    setResult(null);

    const tagList = tags
      .split(",")
      .map((t) => t.trim().toLowerCase())
      .filter((t) => t.length > 0);

    try {
      const res = await fetch("/api/documents", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Tenant-ID": currentTenant,
        },
        body: JSON.stringify({
          title: title.trim(),
          content: content.trim(),
          author: author.trim() || undefined,
          externalId: externalId.trim() || undefined,
          tags: tagList,
          contentType: contentType || "text/plain",
        }),
      });

      const data = await res.json();
      if (res.ok || res.status === 201) {
        setResult(data);
        setTitle("");
        setContent("");
        setTags("");
        setExternalId("");
        onDocumentCreated();
      } else {
        setError(data.detail || "Document indexing failed");
      }
    } catch (err: any) {
      setError(err.message || "Failed to contact document service");
    } finally {
      setLoading(false);
    }
  };

  const copyId = () => {
    if (result?.id) {
      navigator.clipboard.writeText(result.id);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Form */}
      <div className="lg:col-span-2 bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-xl space-y-5">
        <div className="flex items-center justify-between pb-3 border-b border-slate-800">
          <div>
            <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
              <FilePlus className="w-4 h-4 text-indigo-400" />
              Index New Document
            </h2>
            <p className="text-xs text-slate-400">
              Atomically commits to PostgreSQL outbox, then routes to OpenSearch shard.
            </p>
          </div>
          <span className="text-xs font-mono px-2.5 py-1 rounded bg-indigo-950 text-indigo-300 border border-indigo-800">
            Tenant: {currentTenant}
          </span>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1.5">
              Document Title *
            </label>
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Q3 Global Payroll Operating Procedure"
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3.5 py-2.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                Author
              </label>
              <input
                type="text"
                value={author}
                onChange={(e) => setAuthor(e.target.value)}
                placeholder="e.g. jane.doe@acme.com"
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3.5 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                External System ID
              </label>
              <input
                type="text"
                value={externalId}
                onChange={(e) => setExternalId(e.target.value)}
                placeholder="e.g. jira-RUNBOOK-104"
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3.5 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 font-mono text-xs"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                Tags (comma separated)
              </label>
              <input
                type="text"
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                placeholder="e.g. payroll, runbook, finance"
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3.5 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                Content Type
              </label>
              <select
                value={contentType}
                onChange={(e) => setContentType(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3.5 py-2 text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                <option value="text/plain">text/plain</option>
                <option value="text/markdown">text/markdown</option>
                <option value="application/json">application/json</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1.5">
              Document Content *
            </label>
            <textarea
              required
              rows={8}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="Paste or write full document body here (up to 1 MB)..."
              className="w-full bg-slate-950 border border-slate-800 rounded-lg p-3.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 font-mono text-xs leading-relaxed"
            />
          </div>

          <div className="flex items-center justify-end pt-2">
            <button
              type="submit"
              disabled={loading}
              className="flex items-center gap-2 px-6 py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs shadow-lg shadow-indigo-600/30 transition-all disabled:opacity-50"
            >
              <Send className="w-3.5 h-3.5" />
              {loading ? "Indexing..." : "Index Document"}
            </button>
          </div>
        </form>
      </div>

      {/* Status & Outcome Card */}
      <div className="space-y-4">
        {result && (
          <div className="bg-slate-900 border border-emerald-800/80 rounded-xl p-5 shadow-xl space-y-4 animate-in fade-in duration-200">
            <div className="flex items-center gap-2 text-emerald-400 font-bold text-sm">
              <CheckCircle2 className="w-5 h-5" />
              Document Accepted (201 Created)
            </div>

            <div className="space-y-2 text-xs">
              <div className="flex justify-between items-center py-1 border-b border-slate-800">
                <span className="text-slate-400">Indexing State:</span>
                {result.indexingState === "INDEXED" ? (
                  <span className="px-2 py-0.5 rounded font-mono font-bold bg-emerald-950 text-emerald-300 border border-emerald-800">
                    INDEXED (Direct)
                  </span>
                ) : (
                  <span className="px-2 py-0.5 rounded font-mono font-bold bg-amber-950 text-amber-300 border border-amber-800 flex items-center gap-1">
                    <Clock className="w-3 h-3" /> PENDING (Outbox Relay)
                  </span>
                )}
              </div>

              <div className="flex justify-between items-center py-1 border-b border-slate-800">
                <span className="text-slate-400">Tenant ID:</span>
                <span className="font-mono text-indigo-300 font-semibold">{result.tenantId}</span>
              </div>

              <div className="py-1">
                <span className="text-slate-400 block mb-1">Generated UUID:</span>
                <div className="flex items-center justify-between bg-slate-950 px-2.5 py-1.5 rounded border border-slate-800 font-mono text-[11px] text-slate-300">
                  <span className="truncate mr-2">{result.id}</span>
                  <button onClick={copyId} className="text-indigo-400 hover:text-indigo-300">
                    <Copy className="w-3.5 h-3.5" />
                  </button>
                </div>
                {copied && <span className="text-[10px] text-emerald-400">Copied to clipboard</span>}
              </div>
            </div>

            <div className="p-3 bg-slate-950 rounded-lg border border-slate-800/80 text-[11px] text-slate-400 leading-relaxed">
              <strong>Consistency Guarantee:</strong> Strongly consistent read-your-writes from PostgreSQL by ID. OpenSearch index refreshed in ~1s.
            </div>
          </div>
        )}

        {error && (
          <div className="bg-slate-900 border border-rose-800 rounded-xl p-5 shadow-xl space-y-3">
            <div className="flex items-center gap-2 text-rose-400 font-bold text-sm">
              <AlertCircle className="w-5 h-5" />
              Submission Error
            </div>
            <p className="text-xs text-rose-300 leading-relaxed">{error}</p>
          </div>
        )}

        <div className="bg-slate-900/60 border border-slate-800/80 rounded-xl p-5 text-xs text-slate-400 space-y-3">
          <h3 className="font-semibold text-slate-200">Outbox &amp; Sharding Architecture</h3>
          <p className="leading-relaxed">
            1. The write commits to PostgreSQL <code className="text-slate-300">documents</code> and <code className="text-slate-300">outbox_events</code> in a single ACID transaction.
          </p>
          <p className="leading-relaxed">
            2. Shard routing <code className="text-slate-300">routing={currentTenant}</code> directs the search document to exactly 1 shard out of N primaries.
          </p>
          <p className="leading-relaxed">
            3. Redis search cache generation counter <code className="text-slate-300">searchgen:v1:{currentTenant}</code> is automatically incremented in O(1) time.
          </p>
        </div>
      </div>
    </div>
  );
}
