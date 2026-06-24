import {
  ArrowLeft,
  CheckCircle,
  Github,
  Globe,
  Search,
  type LucideIcon,
} from "lucide-react";

import type { Integration } from "../lib/types";

/* ------------------------------------------------------------------ */
/*  Integration icon map                                               */
/* ------------------------------------------------------------------ */

const ICON_MAP: Record<string, LucideIcon> & { __brand?: string } = {
  google: Globe,
  github: Github,
  web_search: Search,
} as Record<string, LucideIcon>;

const LABEL_COLOR_MAP: Record<string, string> = {
  google: "#4285F4",
  github: "#1C1F23",
  web_search: "#8B9099",
};

const BG_COLOR_MAP: Record<string, string> = {
  google: "#E8F0FE",
  github: "#F0F0F1",
  web_search: "#F4F4F6",
};

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export function IntegrationPanel(props: {
  integrations: Integration[];
  loading: boolean;
  error: string | null;
  busyId: string | null;
  onBack: () => void;
  onToggle: (integration: Integration) => void;
  onReconnect: (integration: Integration) => void;
}) {
  const { integrations, loading, error, busyId, onBack } = props;

  const connected = integrations.filter((i) => i.connected);

  return (
    <>
      {/* ── Header ─────────────────────────────── */}
      <div className="back-row">
        <button
          type="button"
          className="icon-button back-button"
          onClick={onBack}
          aria-label="Back"
        >
          <ArrowLeft size={22} strokeWidth={2} />
        </button>
      </div>

      {/* ── Loading / Error ────────────────────── */}
      {loading ? (
        <div className="screen-pad">
          <p className="integration-subtitle">Refreshing integration status…</p>
        </div>
      ) : null}
      {!loading && error ? (
        <div className="screen-pad">
          <p className="login-error">{error}</p>
        </div>
      ) : null}

      {/* ── Connected Tools ────────────────────── */}
      <div className="integrations-section">
        <div className="integrations-section-label">Connected Tools</div>

        {connected.length === 0 && !loading ? (
          <p className="history-empty">No connected tools.</p>
        ) : (
          <div className="integrations-cards">
            {connected.map((integration) => (
              <IntegrationCard
                key={integration.id}
                integration={integration}
                busyId={busyId}
              />
            ))}
          </div>
        )}
      </div>

      {/* ── Available Tools (empty placeholder) ── */}
      <div className="integrations-section">
        <div className="integrations-section-label">Available Tools</div>
        <div className="integrations-empty">
          <Globe size={24} strokeWidth={1.5} color="#d0d2d6" />
          <p className="integrations-empty-text">
            Browse and install new tools for Hugin to use.
          </p>
        </div>
      </div>
    </>
  );
}

/* ------------------------------------------------------------------ */
/*  Single integration card                                            */
/* ------------------------------------------------------------------ */

function IntegrationCard({
  integration,
  busyId,
}: {
  integration: Integration;
  busyId: string | null;
}) {
  const Icon = ICON_MAP[integration.id] ?? CheckCircle;
  const iconColor = LABEL_COLOR_MAP[integration.id] ?? "#1C1F23";
  const iconBg = BG_COLOR_MAP[integration.id] ?? "#F4F4F6";

  return (
    <div className="integration-card">
      {/* Left: icon */}
      <div
        className="integration-card-icon"
        style={{ background: iconBg, color: iconColor }}
      >
        <Icon size={26} strokeWidth={1.7} />
      </div>

      {/* Right: content */}
      <div className="integration-card-body">
        {/* Title row */}
        <div className="integration-card-title-row">
          <span className="integration-card-name">{integration.name}</span>
          <span className="integration-card-badge">
            <CheckCircle size={13} strokeWidth={2.5} />
            Connected
          </span>
        </div>

        {/* Tool chips */}
        {integration.tools.length > 0 && (
          <div className="integration-card-chips">
            {integration.tools.map((tool) => {
              const label = tool
                .replace(/^google_/, "")
                .replace(/^github_/, "")
                .replace(/_/g, " ")
                .replace(/\b\w/g, (c) => c.toUpperCase());
              return (
                <span key={tool} className="integration-chip">
                  {label}
                </span>
              );
            })}
          </div>
        )}

        {/* Status row */}
        <div className="integration-card-status">
          <span
            className={
              busyId === integration.id
                ? "integration-card-status-busy"
                : "integration-card-status-idle"
            }
          >
            {busyId === integration.id
              ? "Reconnecting…"
              : integration.message || `${integration.tools.length} tool${integration.tools.length !== 1 ? "s" : ""} available`}
          </span>
        </div>
      </div>
    </div>
  );
}
