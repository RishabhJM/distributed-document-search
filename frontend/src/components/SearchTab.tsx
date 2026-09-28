"use client";

import React, { useState, useEffect, useRef } from "react";
import { Search, Zap, Clock, Tag, User, Calendar, ExternalLink, Sparkles, X, ChevronRight } from "lucide-react";
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
  const inputRef = useRef<HTMLInputElement>(null);

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
    }, 200);
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
      } else {
        throw new Error();
      }
    } catch {
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
    <div className="space-y-5">
      {/* Command-Bar Spotlight Search Box */}
      <div className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4 shadow-sm backdrop-blur-sm transition-all focus-within:border-zinc-700 focus-within:ring-1 focus-within:ring-zinc-700/60">
        <div className="relative flex items-center">
          <Search className="w-4 h-4 absolute left-3.5 text-zinc-500 pointer-events-none" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search documents by title, tags, or content (e.g. payroll runbook)..."
            className="w-full bg-zinc-950/80 border border-zinc-800/80 rounded-lg pl-10 pr-20 py-2.5 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-zinc-600 text-sm transition-colors font-normal"
          />
          {query ? (
            <button
              onClick={() => setQuery("")}
              className="absolute right-3 p-1 rounded hover:bg-zinc-800 text-zinc-500 hover:text-zinc-300 transition-colors"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          ) : (
            <div className="absolute right-3 flex items-center gap-1.5 text-[11px] font-mono text-zinc-400 bg-zinc-900 px-1.5 py-0.5 rounded border border-zinc-800">
              <span>esc to clear</span>
            </div>
          )}
        </div>

        {/* Query Controls & Options */}
        <div className="flex flex-wrap items-center justify-between gap-3 pt-3 mt-3 border-t border-zinc-800/60 text-xs">
          {/* Left: Options & Active Filter */}
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setFuzzy(!fuzzy)}
              className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium transition-all ${
                fuzzy
                  ? "bg-amber-500/10 text-amber-300 border border-amber-500/30"
                  : "bg-zinc-800/50 text-zinc-400 border border-zinc-850 hover:text-zinc-300"
              }`}
            >
              <Sparkles className="w-3 h-3 text-amber-400" />
              <span>Fuzzy Matching</span>
            </button>

            {selectedTag && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-medium bg-zinc-800 text-zinc-200 border border-zinc-700">
                <Tag className="w-3 h-3 text-zinc-400" />
                <span>tag: {selectedTag}</span>
                <button
                  onClick={() => setSelectedTag(null)}
                  className="hover:text-zinc-50 p-0.5 text-zinc-400"
                >
                  <X className="w-3 h-3" />
                </button>
              </span>
            )}
          </div>

          {/* Right: Quick Samples */}
          <div className="flex items-center gap-1.5 text-[11px] text-zinc-500">
            <span className="text-zinc-500">Try:</span>
            {["payroll runbook", "kubernetes deployment", "api specification"].map((sample) => (
              <button
                key={sample}
                onClick={() => setQuery(sample)}
                className="px-2 py-0.5 rounded bg-zinc-800/60 hover:bg-zinc-800 text-zinc-300 hover:text-zinc-100 transition-colors font-mono"
              >
                {sample}
              </button>
            ))}
          </div>
        </div>

        {/* Facet Tag Pills */}
        {results?.facets?.tags && Object.keys(results.facets.tags).length > 0 && (
          <div className="flex items-center gap-2 pt-2.5 mt-2.5 border-t border-zinc-800/40 flex-wrap text-xs">
            <span className="text-zinc-500 text-[11px] flex items-center gap-1 font-mono uppercase tracking-wider">
              Facets:
            </span>
            {Object.entries(results.facets.tags).map(([tag, count]) => {
              const active = selectedTag === tag;
              return (
                <button
                  key={tag}
                  onClick={() => setSelectedTag(active ? null : tag)}
                  className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-[11px] transition-all font-mono ${
                    active
                      ? "bg-zinc-200 text-zinc-900 font-semibold shadow-sm"
                      : "bg-zinc-800/60 hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 border border-zinc-800"
                  }`}
                >
                  <span>{tag}</span>
                  <span className={`text-[10px] ${active ? "text-zinc-700" : "text-zinc-500"}`}>
                    {count}
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* Telemetry & Performance Indicator Header */}
      {results && (
        <div className="flex items-center justify-between text-xs px-1">
          <div className="flex items-center gap-2">
            <span className="text-zinc-400 font-medium">
              {results.page.totalHits} {results.page.totalHits === 1 ? "document" : "documents"} found
            </span>
            <span className="text-zinc-600">·</span>
            <span className="font-mono text-zinc-500 text-[11px]">
              tenant: {results.tenantId}
            </span>
          </div>

          <div className="flex items-center gap-2 font-mono text-[11px]">
            {results.cached ? (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-medium">
                <Zap className="w-3 h-3 text-emerald-400" />
                <span>{results.tookMs}ms (Redis L2 Hit)</span>
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-zinc-900 text-zinc-400 border border-zinc-800 font-medium">
                <Clock className="w-3 h-3 text-zinc-500" />
                <span>{results.tookMs}ms (OpenSearch BM25)</span>
              </span>
            )}
          </div>
        </div>
      )}

      {/* Error Banner */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-950/30 border border-rose-800/50 text-rose-300 text-xs flex items-start gap-2.5">
          <div className="p-1 rounded bg-rose-900/50 text-rose-200">
            <X className="w-3.5 h-3.5" />
          </div>
          <div>
            <div className="font-semibold text-rose-200">Search Failed</div>
            <div className="text-rose-300/80 mt-0.5">{error}</div>
          </div>
        </div>
      )}

      {/* Loading Skeleton */}
      {loading && !results && (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="p-4 rounded-xl border border-zinc-800/60 bg-zinc-900/30 animate-pulse space-y-2.5">
              <div className="h-4 bg-zinc-800/60 rounded w-1/3" />
              <div className="h-3 bg-zinc-800/40 rounded w-5/6" />
              <div className="h-3 bg-zinc-800/40 rounded w-2/3" />
            </div>
          ))}
        </div>
      )}

      {/* Empty State */}
      {results && results.hits.length === 0 && !loading && (
        <div className="rounded-xl border border-zinc-800/60 bg-zinc-900/20 p-12 text-center space-y-3">
          <div className="inline-flex items-center justify-center w-10 h-10 rounded-full bg-zinc-900 border border-zinc-800 text-zinc-500">
            <Search className="w-5 h-5" />
          </div>
          <h3 className="text-sm font-semibold text-zinc-200">No documents found</h3>
          <p className="text-xs text-zinc-500 max-w-sm mx-auto">
            No matching documents found in tenant <span className="font-mono text-zinc-400">{currentTenant}</span>.
            Try adjusting your query, enabling fuzzy matching, or indexing new documents in the Document Studio tab.
          </p>
        </div>
      )}

      {/* Document Hit Cards List */}
      <div className="space-y-2.5">
        {results?.hits.map((hit) => (
          <div
            key={hit.id}
            onClick={() => handleCardClick(hit)}
            className="group relative p-4 rounded-xl border border-zinc-850 bg-zinc-900/40 hover:bg-zinc-900/80 hover:border-zinc-700/80 transition-all cursor-pointer shadow-sm"
          >
            <div className="flex items-start justify-between gap-4">
              <div className="space-y-1.5 flex-1 min-w-0">
                {/* Title & External ID */}
                <div className="flex items-center gap-2 flex-wrap">
                  <h3 className="font-medium text-sm text-zinc-100 group-hover:text-white transition-colors truncate">
                    {hit.title}
                  </h3>
                  {hit.externalId && (
                    <span className="font-mono text-[10px] text-zinc-500 bg-zinc-900 px-1.5 py-0.5 rounded border border-zinc-800">
                      {hit.externalId}
                    </span>
                  )}
                </div>

                {/* Highlighted Snippet */}
                <p
                  className="text-xs text-zinc-400 line-clamp-2 leading-relaxed"
                  dangerouslySetInnerHTML={{ __html: hit.snippet }}
                />

                {/* Metadata & Tag Badges */}
                <div className="flex items-center gap-3 pt-1 text-[11px] text-zinc-500 flex-wrap">
                  {hit.author && (
                    <span className="flex items-center gap-1 text-zinc-400">
                      <User className="w-3 h-3 text-zinc-500" />
                      <span>{hit.author}</span>
                    </span>
                  )}

                  <span className="flex items-center gap-1 text-zinc-500">
                    <Calendar className="w-3 h-3 text-zinc-600" />
                    <span>{new Date(hit.createdAt).toLocaleDateString()}</span>
                  </span>

                  {hit.tags && hit.tags.length > 0 && (
                    <div className="flex items-center gap-1">
                      {hit.tags.map((t) => (
                        <span
                          key={t}
                          className="font-mono text-[10px] text-zinc-400 bg-zinc-900 px-1.5 py-0.2 rounded border border-zinc-800"
                        >
                          {t}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* Right Side: Relevance Score & Arrow */}
              <div className="flex items-center gap-3 shrink-0">
                <div className="text-right">
                  <div className="text-[10px] font-mono uppercase tracking-wider text-zinc-500">
                    BM25 Score
                  </div>
                  <div className="font-mono text-xs font-semibold text-zinc-300">
                    {hit.score.toFixed(2)}
                  </div>
                </div>
                <ChevronRight className="w-4 h-4 text-zinc-600 group-hover:text-zinc-300 group-hover:translate-x-0.5 transition-all" />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
