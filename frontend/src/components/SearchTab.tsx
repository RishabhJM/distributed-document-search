"use client";

import React, { useState, useEffect } from "react";
import { Search, Zap, Clock, Tag, User, Calendar, ExternalLink, Sparkles } from "lucide-react";
import { DocDetail } from "./DocModal";

interface SearchHit {
  id: string;
  externalId?: string;
  title: string;
  snippet: string;
  score: number;
  author?: string;
  tags?: string[];
  createdAt: string;
}

interface SearchResponse {
  query: string;
  tenantId: string;
  hits: SearchHit[];
  page: {
    from: number;
    size: number;
    totalHits: number;
    totalIsLowerBound: boolean;
  };
  tookMs: number;
  cached: boolean;
  facets: {
    tags?: Record<string, number>;
  };
}

interface SearchTabProps {
  currentTenant: string;
  onSelectDoc: (doc: DocDetail) => void;
}

export default function SearchTab({ currentTenant, onSelectDoc }: SearchTabProps) {
  const [query, setQuery] = useState("");
  const [fuzzy, setFuzzy] = useState(false);
  const [selectedTag, setSelectedTag] = useState<string | null>(null);
  const [results, setResults] = useState<SearchResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const performSearch = async (searchQuery: string, isFuzzy: boolean, tagFilter: string | null) => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      if (searchQuery.trim()) params.set("q", searchQuery.trim());
      if (isFuzzy) params.set("fuzzy", "true");
      if (tagFilter) params.set("tags", tagFilter);
      params.set("highlight", "true");
      params.set("size", "25");

      const res = await fetch(`/api/search?${params.toString()}`, {
        headers: {
          "X-Tenant-ID": currentTenant,
        },
      });

      if (res.ok) {
        const data: SearchResponse = await res.json();
        setResults(data);
      } else {
        const err = await res.json();
        setError(err.detail || "Search request failed");
      }
    } catch (e: any) {
      setError(e.message || "Failed to contact search service");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const handler = setTimeout(() => {
      performSearch(query, fuzzy, selectedTag);
    }, 250);
    return () => clearTimeout(handler);
  }, [query, fuzzy, selectedTag, currentTenant]);

  const handleCardClick = async (hit: SearchHit) => {
    try {
      const res = await fetch(`/api/documents/${hit.id}`, {
        headers: {
          "X-Tenant-ID": currentTenant,
        },
      });
      if (res.ok) {
        const doc: DocDetail = await res.json();
        onSelectDoc(doc);
      }
    } catch {
      // Fallback detail
      onSelectDoc({
        id: hit.id,
        tenantId: currentTenant,
        externalId: hit.externalId,
        title: hit.title,
        content: hit.snippet.replace(/<\/?em>/g, ""),
        author: hit.author,
        tags: hit.tags,
        createdAt: hit.createdAt,
        updatedAt: hit.createdAt,
      });
    }
  };

  return (
    <div className="space-y-6">
      {/* Search Bar Controls */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-lg space-y-4">
        <div className="relative">
          <Search className="w-5 h-5 absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search documents by title, tags, author, content (e.g. payroll runbook)..."
            className="w-full bg-slate-950 border border-slate-800 rounded-lg pl-12 pr-4 py-3 text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm"
          />
        </div>

        <div className="flex flex-wrap items-center justify-between gap-4 pt-2">
          {/* Options */}
          <div className="flex items-center gap-4 text-xs text-slate-300">
            <label className="flex items-center gap-2 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={fuzzy}
                onChange={(e) => setFuzzy(e.target.checked)}
                className="w-4 h-4 rounded bg-slate-800 border-slate-700 text-indigo-600 focus:ring-indigo-500 focus:ring-offset-slate-900"
              />
              <span className="flex items-center gap-1 font-medium">
                <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                Fuzzy Match (Auto-Levenshtein)
              </span>
            </label>

            {selectedTag && (
              <button
                onClick={() => setSelectedTag(null)}
                className="text-xs px-2.5 py-1 rounded bg-indigo-950 text-indigo-300 border border-indigo-800 flex items-center gap-1 hover:bg-indigo-900"
              >
                Filtered: <span className="font-semibold">{selectedTag}</span> ×
              </button>
            )}
          </div>

          {/* Quick query chips */}
          <div className="flex items-center gap-2 text-xs">
            <span className="text-slate-500">Quick queries:</span>
            {["payroll runbook", "kubernetes deployment", "api specification", "incident response"].map((sample) => (
              <button
                key={sample}
                onClick={() => setQuery(sample)}
                className="px-2.5 py-1 rounded-md bg-slate-800 text-slate-300 hover:bg-slate-700 transition-colors"
              >
                {sample}
              </button>
            ))}
          </div>
        </div>

        {/* Facet Tags */}
        {results?.facets?.tags && Object.keys(results.facets.tags).length > 0 && (
          <div className="pt-2 border-t border-slate-800/60 flex items-center gap-2 flex-wrap text-xs">
            <span className="text-slate-400 font-medium flex items-center gap-1">
              <Tag className="w-3.5 h-3.5 text-slate-500" /> Facets:
            </span>
            {Object.entries(results.facets.tags).map(([tag, count]) => (
              <button
                key={tag}
                onClick={() => setSelectedTag(selectedTag === tag ? null : tag)}
                className={`px-2 py-0.5 rounded font-mono transition-all ${
                  selectedTag === tag
                    ? "bg-indigo-600 text-white font-bold"
                    : "bg-slate-800 text-slate-300 hover:bg-slate-700"
                }`}
              >
                {tag} <span className="opacity-60">({count})</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Results Header Status */}
      {results && (
        <div className="flex flex-wrap items-center justify-between gap-3 px-1 text-xs">
          <div className="text-slate-400 font-medium">
            Found <span className="text-slate-100 font-bold">{results.page.totalHits}</span> results for{" "}
            <span className="text-slate-100 font-semibold">&ldquo;{results.query || "*"}&rdquo;</span> in{" "}
            <span className="text-indigo-400 font-mono font-semibold">{results.tenantId}</span>
          </div>

          <div className="flex items-center gap-2.5">
            {/* Cache indicator badge */}
            {results.cached ? (
              <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-950 border border-emerald-700/60 text-emerald-300 font-medium shadow-sm">
                <Zap className="w-3.5 h-3.5 text-emerald-400" />
                Served from cache (Redis L2)
              </div>
            ) : (
              <div className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-slate-900 border border-slate-800 text-slate-400">
                Fresh from OpenSearch (BM25)
              </div>
            )}

            {/* Latency badge */}
            <div className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-slate-900 border border-slate-800 text-slate-300 font-mono">
              <Clock className="w-3 h-3 text-slate-500" />
              {results.tookMs} ms
            </div>
          </div>
        </div>
      )}

      {/* Error state */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-950/40 border border-rose-800/80 text-rose-300 text-sm">
          {error}
        </div>
      )}

      {/* Hit Cards */}
      <div className="space-y-3">
        {loading && (
          <div className="py-12 text-center text-slate-500 text-sm">
            Searching OpenSearch cluster...
          </div>
        )}

        {!loading && results && results.hits.length === 0 && (
          <div className="py-16 text-center text-slate-500 text-sm bg-slate-900/40 rounded-xl border border-slate-900">
            No matching documents found in tenant <span className="font-mono text-slate-400">{currentTenant}</span>.
          </div>
        )}

        {!loading &&
          results?.hits.map((hit) => (
            <div
              key={hit.id}
              onClick={() => handleCardClick(hit)}
              className="bg-slate-900/90 border border-slate-800/80 hover:border-indigo-500/50 rounded-xl p-5 cursor-pointer transition-all hover:shadow-lg hover:shadow-indigo-950/20 group space-y-2.5"
            >
              <div className="flex items-start justify-between gap-4">
                <h3 className="text-base font-semibold text-slate-100 group-hover:text-indigo-300 transition-colors flex items-center gap-2">
                  {hit.title}
                  <ExternalLink className="w-3.5 h-3.5 opacity-0 group-hover:opacity-100 transition-opacity text-indigo-400" />
                </h3>
                <div className="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700/60 shrink-0">
                  BM25: {hit.score.toFixed(2)}
                </div>
              </div>

              {/* Highlight snippet */}
              <div
                className="text-xs text-slate-300 leading-relaxed font-sans"
                dangerouslySetInnerHTML={{
                  __html: hit.snippet || "No snippet preview available.",
                }}
              />

              {/* Metadata */}
              <div className="flex flex-wrap items-center justify-between gap-3 pt-1 text-[11px] text-slate-400">
                <div className="flex items-center gap-3">
                  {hit.author && (
                    <span className="flex items-center gap-1">
                      <User className="w-3 h-3 text-slate-500" />
                      {hit.author}
                    </span>
                  )}
                  <span className="flex items-center gap-1">
                    <Calendar className="w-3 h-3 text-slate-500" />
                    {new Date(hit.createdAt).toLocaleDateString()}
                  </span>
                  {hit.externalId && (
                    <span className="font-mono text-slate-500">
                      ext: {hit.externalId}
                    </span>
                  )}
                </div>

                {hit.tags && hit.tags.length > 0 && (
                  <div className="flex items-center gap-1">
                    {hit.tags.map((t) => (
                      <span
                        key={t}
                        className="px-2 py-0.5 rounded bg-slate-800 text-slate-400 text-[10px] font-mono"
                      >
                        #{t}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}
      </div>
    </div>
  );
}
