"use client";

import React, { useState } from "react";
import { Send, CheckCircle2, Clock, AlertCircle, FilePlus, Copy, Database, Search, Zap, ArrowRight } from "lucide-react";

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
        setError(data.detail || "Failed to index document");
      }
    } catch (err: any) {
      setError(err.message || "Failed to contact service");
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
      {/* Left Column: Minimalist Document Editor Form */}
      <div className="lg:col-span-7 rounded-xl border border-zinc-800 bg-zinc-900/40 p-5 shadow-sm space-y-4">
        <div className="flex items-center justify-between pb-3 border-b border-zinc-800/80">
          <div>
            <h2 className="text-sm font-semibold text-zinc-100 flex items-center gap-2">
              <FilePlus className="w-4 h-4 text-zinc-300" />
              Document Ingestion Studio
            </h2>
            <p className="text-[11px] text-zinc-500 mt-0.5">
              Writes commit atomically to PostgreSQL and propagate to OpenSearch via Outbox.
            </p>
          </div>
          <span className="font-mono text-[11px] text-zinc-400 bg-zinc-900 px-2 py-0.5 rounded border border-zinc-800">
            Tenant: {currentTenant}
          </span>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 text-xs">
          {/* Title */}
          <div className="space-y-1.5">
            <label className="block text-[11px] font-medium text-zinc-300 uppercase tracking-wider font-mono">
              Title <span className="text-rose-400">*</span>
            </label>
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Q4 Infrastructure Disaster Recovery Runbook"
              className="w-full bg-zinc-950/80 border border-zinc-800 rounded-lg px-3 py-2 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-zinc-600 text-xs transition-colors"
            />
          </div>

          {/* Grid of External ID, Author, Tags */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="space-y-1.5">
              <label className="block text-[11px] font-medium text-zinc-400 font-mono">
                External ID
              </label>
              <input
                type="text"
                value={externalId}
                onChange={(e) => setExternalId(e.target.value)}
                placeholder="runbook-402"
                className="w-full bg-zinc-950/80 border border-zinc-800 rounded-lg px-3 py-2 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-zinc-600 text-xs font-mono transition-colors"
              />
            </div>

            <div className="space-y-1.5">
              <label className="block text-[11px] font-medium text-zinc-400 font-mono">
                Author
              </label>
              <input
                type="text"
                value={author}
                onChange={(e) => setAuthor(e.target.value)}
                placeholder="e.g. s.patel"
                className="w-full bg-zinc-950/80 border border-zinc-800 rounded-lg px-3 py-2 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-zinc-600 text-xs transition-colors"
              />
            </div>

            <div className="space-y-1.5">
              <label className="block text-[11px] font-medium text-zinc-400 font-mono">
                Tags (csv)
              </label>
              <input
                type="text"
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                placeholder="ops, infra, runbook"
                className="w-full bg-zinc-950/80 border border-zinc-800 rounded-lg px-3 py-2 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-zinc-600 text-xs transition-colors"
              />
            </div>
          </div>

          {/* Document Content */}
          <div className="space-y-1.5">
            <label className="block text-[11px] font-medium text-zinc-300 uppercase tracking-wider font-mono">
              Document Content <span className="text-rose-400">*</span>
            </label>
            <textarea
              required
              rows={6}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="Enter markdown or text content here. Terms will be indexed with BM25 analyzer in OpenSearch..."
              className="w-full bg-zinc-950/80 border border-zinc-800 rounded-lg p-3 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-zinc-600 text-xs leading-relaxed font-mono transition-colors"
            />
          </div>

          {/* Error Banner */}
          {error && (
            <div className="p-3 rounded-lg bg-rose-950/30 border border-rose-800/60 text-rose-300 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {/* Submit Button */}
          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="submit"
              disabled={loading || !title.trim() || !content.trim()}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-zinc-100 hover:bg-white text-zinc-900 font-medium text-xs transition-all shadow-sm disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              <Send className="w-3.5 h-3.5" />
              <span>{loading ? "Committing ACID Transaction..." : "Index Document"}</span>
            </button>
          </div>
        </form>
      </div>

      {/* Right Column: Outbox Architecture Pipeline Visualizer & Live Result */}
      <div className="lg:col-span-5 space-y-4">
        {/* Outbox Pipeline Card */}
        <div className="rounded-xl border border-zinc-800 bg-zinc-900/30 p-5 shadow-sm space-y-3.5">
          <div className="flex items-center justify-between pb-2 border-b border-zinc-800/80">
            <h3 className="text-xs font-semibold text-zinc-200 uppercase tracking-wider font-mono">
              Transactional Outbox Pipeline
            </h3>
            <span className="font-mono text-[10px] text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
              Guaranteed Delivery
            </span>
          </div>

          <div className="space-y-3 text-xs">
            {/* Step 1 */}
            <div className="flex items-start gap-3 p-2.5 rounded-lg bg-zinc-900/60 border border-zinc-800/60">
              <div className="p-1.5 rounded bg-zinc-800 text-zinc-300">
                <Database className="w-3.5 h-3.5" />
              </div>
              <div>
                <div className="font-medium text-zinc-200 text-xs">1. PostgreSQL ACID Commit</div>
                <div className="text-[11px] text-zinc-400 mt-0.5 leading-normal">
                  Inserts to <span className="font-mono text-zinc-300">documents</span> and <span className="font-mono text-zinc-300">outbox_events</span> commit in one atomic transaction.
                </div>
              </div>
            </div>

            {/* Step 2 */}
            <div className="flex items-start gap-3 p-2.5 rounded-lg bg-zinc-900/60 border border-zinc-800/60">
              <div className="p-1.5 rounded bg-zinc-800 text-zinc-300">
                <Search className="w-3.5 h-3.5" />
              </div>
              <div>
                <div className="font-medium text-zinc-200 text-xs">2. OpenSearch Shard Sync</div>
                <div className="text-[11px] text-zinc-400 mt-0.5 leading-normal">
                  Direct sync via <span className="font-mono text-zinc-300">routing={currentTenant}</span>. If offline, the outbox relay auto-reconciles within 2s.
                </div>
              </div>
            </div>

            {/* Step 3 */}
            <div className="flex items-start gap-3 p-2.5 rounded-lg bg-zinc-900/60 border border-zinc-800/60">
              <div className="p-1.5 rounded bg-zinc-800 text-zinc-300">
                <Zap className="w-3.5 h-3.5" />
              </div>
              <div>
                <div className="font-medium text-zinc-200 text-xs">3. O(1) Cache Invalidation</div>
                <div className="text-[11px] text-zinc-400 mt-0.5 leading-normal">
                  Atomically increments <span className="font-mono text-zinc-300">searchgen:v1:{currentTenant}</span>, instantly invalidating stale queries without keyspace scans.
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Live Indexing Response */}
        {result && (
          <div className="rounded-xl border border-emerald-500/30 bg-emerald-950/20 p-5 shadow-sm space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-emerald-400 font-semibold text-xs">
                <CheckCircle2 className="w-4 h-4" />
                <span>Document Created Successfully</span>
              </div>
              <span className={`font-mono text-[10px] font-bold px-2 py-0.5 rounded border ${
                result.indexingState === "INDEXED"
                  ? "bg-emerald-500/10 text-emerald-300 border-emerald-500/30"
                  : "bg-amber-500/10 text-amber-300 border-amber-500/30"
              }`}>
                {result.indexingState}
              </span>
            </div>

            <div className="space-y-1.5 text-xs text-zinc-400">
              <div className="flex items-center justify-between text-[11px] font-mono bg-zinc-950/60 p-2 rounded border border-zinc-800">
                <span className="text-zinc-500 truncate mr-2">UUID: {result.id}</span>
                <button
                  onClick={() => handleCopy(result.id)}
                  className="hover:text-white transition-colors p-1"
                  title="Copy UUID"
                >
                  <Copy className="w-3 h-3 text-zinc-400" />
                </button>
              </div>
              {copied && (
                <div className="text-[10px] text-emerald-400 text-right">
                  Copied UUID to clipboard!
                </div>
              )}
              <div className="text-[11px] text-zinc-400 pt-1">
                Document is immediately readable by UUID from PostgreSQL. Search index refreshes within ~1s.
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
