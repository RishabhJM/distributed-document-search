"use client";

import React, { useEffect, useState } from "react";
import { Server, Database, Search, HardDrive, Layers, RefreshCw } from "lucide-react";

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
    const interval = setInterval(fetchHealth, 8000);
    return () => clearInterval(interval);
  }, []);

  const getStatusDot = (status?: string) => {
    if (status === "UP") return "bg-emerald-500 shadow-emerald-500/50";
    if (status === "DEGRADED") return "bg-amber-500 shadow-amber-500/50 animate-pulse";
    return "bg-rose-500 shadow-rose-500/50";
  };

  return (
    <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-4 py-3 flex flex-wrap items-center justify-between gap-4">
        {/* Brand */}
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-indigo-600/20 border border-indigo-500/30 text-indigo-400">
            <Search className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-base font-bold text-slate-100 flex items-center gap-2">
              Distributed Document Search
              <span className="text-xs px-2 py-0.5 rounded-full font-mono bg-indigo-950 text-indigo-300 border border-indigo-800">
                v1.0
              </span>
            </h1>
            <p className="text-xs text-slate-400">
              Multi-Tenant BM25 · Outbox Relay · Shard Routing
            </p>
          </div>
        </div>

        {/* Live Dependency Dots */}
        <div className="flex items-center gap-3 bg-slate-950/60 border border-slate-800/80 px-3 py-1.5 rounded-full text-xs">
          <span className="text-slate-400 font-medium mr-1 flex items-center gap-1.5">
            <Layers className="w-3.5 h-3.5 text-slate-500" />
            Topology:
          </span>

          <div className="flex items-center gap-1.5" title="PostgreSQL 16 (Source of Truth)">
            <Database className="w-3.5 h-3.5 text-slate-400" />
            <span className={`w-2 h-2 rounded-full shadow-sm ${getStatusDot(health?.dependencies?.postgres?.status)}`} />
            <span className="text-slate-300 font-mono text-[11px]">PG</span>
          </div>

          <span className="text-slate-700">·</span>

          <div className="flex items-center gap-1.5" title="OpenSearch 2.18 (Derived Index)">
            <Search className="w-3.5 h-3.5 text-slate-400" />
            <span className={`w-2 h-2 rounded-full shadow-sm ${getStatusDot(health?.dependencies?.opensearch?.status)}`} />
            <span className="text-slate-300 font-mono text-[11px]">OS</span>
          </div>

          <span className="text-slate-700">·</span>

          <div className="flex items-center gap-1.5" title="Redis 7.4 (Cache & Rate Limiting)">
            <HardDrive className="w-3.5 h-3.5 text-slate-400" />
            <span className={`w-2 h-2 rounded-full shadow-sm ${getStatusDot(health?.dependencies?.redis?.status)}`} />
            <span className="text-slate-300 font-mono text-[11px]">Redis</span>
          </div>

          <button
            onClick={fetchHealth}
            className="ml-1 text-slate-500 hover:text-slate-300 transition-colors"
            title="Refresh health"
          >
            <RefreshCw className={`w-3 h-3 ${loadingHealth ? "animate-spin" : ""}`} />
          </button>
        </div>

        {/* Tenant Selector */}
        <div className="flex items-center gap-2">
          <label htmlFor="tenant-select" className="text-xs text-slate-400 font-medium">
            Active Tenant:
          </label>
          <div className="relative">
            <select
              id="tenant-select"
              value={currentTenant}
              onChange={(e) => onTenantChange(e.target.value)}
              className="bg-slate-800 border border-slate-700 text-slate-100 text-xs font-semibold rounded-lg px-3 py-1.5 pr-8 focus:ring-2 focus:ring-indigo-500 focus:outline-none cursor-pointer"
            >
              <option value="acme">acme (Enterprise · 50 rps)</option>
              <option value="globex">globex (Enterprise · 100 rps)</option>
              <option value="initech">initech (Standard · 20 rps)</option>
            </select>
          </div>
        </div>
      </div>
    </header>
  );
}
