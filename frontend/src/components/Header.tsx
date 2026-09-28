"use client";

import React, { useEffect, useState } from "react";
import { Database, Search, HardDrive, RefreshCw, Cpu, CheckCircle2, AlertTriangle, XCircle } from "lucide-react";

export interface HealthData {
  status: string;
  dependencies: {
    postgres?: { status: string; critical: boolean };
    opensearch?: { status: string; critical: boolean };
    redis?: { status: string; critical: boolean };
  };
}

interface HeaderProps {
  currentTenant: string;
  onTenantChange: (tenant: string) => void;
  onRefreshHealth?: () => void;
}

export default function Header({ currentTenant, onTenantChange }: HeaderProps) {
  const [health, setHealth] = useState<HealthData | null>(null);
  const [loadingHealth, setLoadingHealth] = useState(false);

  const fetchHealth = async () => {
    setLoadingHealth(true);
    try {
      const res = await fetch("/api/health");
      if (res.ok) {
        const data = await res.json();
        setHealth(data);
      } else {
        setHealth({ status: "DOWN", dependencies: {} });
      }
    } catch {
      setHealth({ status: "DOWN", dependencies: {} });
    } finally {
      setLoadingHealth(false);
    }
  };

  useEffect(() => {
    fetchHealth();
    const interval = setInterval(fetchHealth, 10000);
    return () => clearInterval(interval);
  }, []);

  const getStatusDot = (status?: string) => {
    if (status === "UP") return "bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.5)]";
    if (status === "DEGRADED") return "bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.5)] animate-pulse";
    return "bg-rose-500 shadow-[0_0_8px_rgba(244,63,94,0.5)]";
  };

  const getOverallBadge = () => {
    if (!health) return null;
    if (health.status === "UP") {
      return (
        <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
          Healthy
        </span>
      );
    }
    if (health.status === "DEGRADED") {
      return (
        <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-medium bg-amber-500/10 text-amber-400 border border-amber-500/20">
          <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />
          Degraded (Fail-Open)
        </span>
      );
    }
    return (
      <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-medium bg-rose-500/10 text-rose-400 border border-rose-500/20">
        <span className="w-1.5 h-1.5 rounded-full bg-rose-400" />
        Offline
      </span>
    );
  };

  return (
    <header className="border-b border-zinc-800/80 bg-zinc-950/80 backdrop-blur-md sticky top-0 z-40 transition-colors">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-2.5 flex items-center justify-between gap-4">
        {/* Brand & System Identity */}
        <div className="flex items-center gap-3.5">
          <div className="relative flex items-center justify-center w-8 h-8 rounded-lg bg-zinc-900 border border-zinc-700/60 shadow-inner">
            <Cpu className="w-4 h-4 text-zinc-200" />
            <span className="absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full bg-emerald-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-semibold text-sm tracking-tight text-zinc-100">
                DeepRunner
              </span>
              <span className="text-[11px] text-zinc-400 tracking-tight font-medium">
                Search Engine
              </span>
              <span className="font-mono text-[10px] text-zinc-400 px-1.5 py-0.2 rounded bg-zinc-900 border border-zinc-800">
                10M+ Scale
              </span>
            </div>
          </div>
        </div>

        {/* Live Topology Cluster & Overall Status */}
        <div className="hidden md:flex items-center gap-3">
          <div className="flex items-center gap-3.5 px-3 py-1.5 rounded-lg bg-zinc-900/60 border border-zinc-800/80 text-xs">
            <div className="flex items-center gap-1.5 text-zinc-400" title="PostgreSQL 16 · ACID Source of Truth">
              <Database className="w-3.5 h-3.5 text-zinc-400" />
              <span className="font-mono text-[11px] text-zinc-300">PG 16</span>
              <span className={`w-1.5 h-1.5 rounded-full ${getStatusDot(health?.dependencies?.postgres?.status)}`} />
            </div>

            <span className="text-zinc-700">·</span>

            <div className="flex items-center gap-1.5 text-zinc-400" title="OpenSearch 2.18 · Shard Routed BM25">
              <Search className="w-3.5 h-3.5 text-zinc-400" />
              <span className="font-mono text-[11px] text-zinc-300">OS 2.18</span>
              <span className={`w-1.5 h-1.5 rounded-full ${getStatusDot(health?.dependencies?.opensearch?.status)}`} />
            </div>

            <span className="text-zinc-700">·</span>

            <div className="flex items-center gap-1.5 text-zinc-400" title="Redis 7.4 · L2 Cache & Token Bucket">
              <HardDrive className="w-3.5 h-3.5 text-zinc-400" />
              <span className="font-mono text-[11px] text-zinc-300">Redis 7.4</span>
              <span className={`w-1.5 h-1.5 rounded-full ${getStatusDot(health?.dependencies?.redis?.status)}`} />
            </div>

            <button
              onClick={fetchHealth}
              disabled={loadingHealth}
              className="text-zinc-400 hover:text-zinc-300 transition-colors ml-1 p-0.5"
              title="Ping topology status"
            >
              <RefreshCw className={`w-3 h-3 ${loadingHealth ? "animate-spin text-zinc-300" : ""}`} />
            </button>
          </div>

          {getOverallBadge()}
        </div>

        {/* Tenant Selector */}
        <div className="flex items-center gap-2.5">
          <label htmlFor="tenant-select" className="text-[11px] uppercase tracking-wider text-zinc-400 font-mono font-medium">
            Tenant:
          </label>
          <div className="relative">
            <select
              id="tenant-select"
              value={currentTenant}
              onChange={(e) => onTenantChange(e.target.value)}
              className="bg-zinc-900 border border-zinc-700/80 hover:border-zinc-600 text-zinc-100 text-xs font-medium rounded-lg px-2.5 py-1.5 pr-7 focus:ring-1 focus:ring-zinc-400 focus:outline-none cursor-pointer transition-colors shadow-sm"
            >
              <option value="acme">acme (Enterprise · 50 RPS)</option>
              <option value="globex">globex (Enterprise · 100 RPS)</option>
              <option value="initech">initech (Standard · 20 RPS)</option>
            </select>
            <div className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-zinc-400">
              <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
              </svg>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}
