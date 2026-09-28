"use client";

import React, { useEffect, useState } from "react";
import { Activity, ShieldCheck, Database, Search, HardDrive, RefreshCw, Layers } from "lucide-react";
import { HealthData } from "./Header";

interface HealthTabProps {
  currentTenant: string;
}

export default function HealthTab({ currentTenant }: HealthTabProps) {
  const [health, setHealth] = useState<HealthData | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchHealth = async () => {
    setLoading(true);
    try {
      const res = await fetch("/api/health");
      if (res.ok) {
        const data = await res.json();
        setHealth(data);
      }
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHealth();
  }, []);

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-xl flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
            <Activity className="w-4 h-4 text-emerald-400" />
            System Topology &amp; Dependency Health
          </h2>
          <p className="text-xs text-slate-400">
            Real-time status of multi-tenant storage, search shards, and distributed token buckets.
          </p>
        </div>
        <button
          onClick={fetchHealth}
          disabled={loading}
          className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-200 transition-colors disabled:opacity-50"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          Refresh Status
        </button>
      </div>

      {/* Dependency Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {/* Postgres */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-5 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-slate-200 font-semibold text-sm">
              <Database className="w-4 h-4 text-indigo-400" />
              PostgreSQL 16
            </div>
            <span
              className={`text-[11px] font-mono px-2 py-0.5 rounded font-bold ${
                health?.dependencies?.postgres?.status === "UP"
                  ? "bg-emerald-950 text-emerald-300 border border-emerald-800"
                  : "bg-rose-950 text-rose-300 border border-rose-800"
              }`}
            >
              {health?.dependencies?.postgres?.status || "UNKNOWN"}
            </span>
          </div>
          <div className="text-xs text-slate-400 space-y-1">
            <div>Role: <span className="text-slate-200 font-medium">Source of Truth</span></div>
            <div>Failure Policy: <span className="text-rose-400 font-medium font-mono">FATAL (HTTP 503)</span></div>
            <div>Isolation: <span className="text-slate-200 font-medium">Tenant FK + Index Scopes</span></div>
          </div>
        </div>

        {/* OpenSearch */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-5 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-slate-200 font-semibold text-sm">
              <Search className="w-4 h-4 text-amber-400" />
              OpenSearch 2.18
            </div>
            <span
              className={`text-[11px] font-mono px-2 py-0.5 rounded font-bold ${
                health?.dependencies?.opensearch?.status === "UP"
                  ? "bg-emerald-950 text-emerald-300 border border-emerald-800"
                  : "bg-rose-950 text-rose-300 border border-rose-800"
              }`}
            >
              {health?.dependencies?.opensearch?.status || "UNKNOWN"}
            </span>
          </div>
          <div className="text-xs text-slate-400 space-y-1">
            <div>Role: <span className="text-slate-200 font-medium">Derived Inverted Index</span></div>
            <div>Failure Policy: <span className="text-rose-400 font-medium font-mono">FATAL for Search</span></div>
            <div>Routing: <span className="text-slate-200 font-medium font-mono">routing=tenantId</span></div>
          </div>
        </div>

        {/* Redis */}
        <div className="bg-slate-900/90 border border-slate-800 rounded-xl p-5 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-slate-200 font-semibold text-sm">
              <HardDrive className="w-4 h-4 text-rose-400" />
              Redis 7.4
            </div>
            <span
              className={`text-[11px] font-mono px-2 py-0.5 rounded font-bold ${
                health?.dependencies?.redis?.status === "UP"
                  ? "bg-emerald-950 text-emerald-300 border border-emerald-800"
                  : "bg-amber-950 text-amber-300 border border-amber-800 animate-pulse"
              }`}
            >
              {health?.dependencies?.redis?.status || "UNKNOWN"}
            </span>
          </div>
          <div className="text-xs text-slate-400 space-y-1">
            <div>Role: <span className="text-slate-200 font-medium">L2 Cache &amp; Rate Limiter</span></div>
            <div>Failure Policy: <span className="text-amber-400 font-medium font-mono">NON-FATAL (Fails Open)</span></div>
            <div>Degradation: <span className="text-slate-200 font-medium">In-Process Token Bucket</span></div>
          </div>
        </div>
      </div>

      {/* Asymmetric Degradation Matrix Table */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-xl space-y-4">
        <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
          <ShieldCheck className="w-4 h-4 text-indigo-400" />
          Architectural Asymmetry: Fail-Closed vs. Fail-Open
        </h3>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="border-b border-slate-800 text-slate-400">
                <th className="py-2.5 px-3">Control / Component</th>
                <th className="py-2.5 px-3">On Dependency Failure</th>
                <th className="py-2.5 px-3">Behavior</th>
                <th className="py-2.5 px-3">Architectural Rationale</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-sans text-slate-300">
              <tr>
                <td className="py-2.5 px-3 font-semibold text-slate-200">Tenant Resolution</td>
                <td className="py-2.5 px-3 text-rose-400 font-mono font-bold">FAILS CLOSED</td>
                <td className="py-2.5 px-3">Returns HTTP 403 / 400 immediately</td>
                <td className="py-2.5 px-3 text-slate-400">Security &amp; isolation controls may never degrade or bypass.</td>
              </tr>
              <tr>
                <td className="py-2.5 px-3 font-semibold text-slate-200">Rate Limiter</td>
                <td className="py-2.5 px-3 text-amber-400 font-mono font-bold">FAILS OPEN</td>
                <td className="py-2.5 px-3">Engages in-process fallback bucket</td>
                <td className="py-2.5 px-3 text-slate-400">Limiter protects availability; it must not cause an outage itself.</td>
              </tr>
              <tr>
                <td className="py-2.5 px-3 font-semibold text-slate-200">Search Cache</td>
                <td className="py-2.5 px-3 text-amber-400 font-mono font-bold">FAILS OPEN</td>
                <td className="py-2.5 px-3">Passes through to OpenSearch origin</td>
                <td className="py-2.5 px-3 text-slate-400">Latency cost only; functional correctness is 100% maintained.</td>
              </tr>
              <tr>
                <td className="py-2.5 px-3 font-semibold text-slate-200">OpenSearch Outage</td>
                <td className="py-2.5 px-3 text-indigo-400 font-mono font-bold">SELF-HEALING</td>
                <td className="py-2.5 px-3">Postgres writes succeed; Outbox stays PENDING</td>
                <td className="py-2.5 px-3 text-slate-400">Scheduled relay heals the search index automatically upon recovery.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
