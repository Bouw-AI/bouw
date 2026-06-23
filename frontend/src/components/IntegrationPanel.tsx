import { ArrowLeft, Puzzle, RefreshCw } from "lucide-react";

import type { Integration } from "../lib/types";

/** Connected-services management screen (Google, GitHub, …). */
export function IntegrationPanel(props: {
  integrations: Integration[];
  loading: boolean;
  error: string | null;
  busyId: string | null;
  onBack: () => void;
  onToggle: (integration: Integration) => void;
  onReconnect: (integration: Integration) => void;
}) {
  const { integrations, loading, error, busyId, onBack, onToggle, onReconnect } = props;

  return (
    <>
      <div className="back-row">
        <button type="button" className="icon-button back-button" onClick={onBack} aria-label="Back">
          <ArrowLeft size={22} strokeWidth={2} />
        </button>
      </div>

      <div className="screen-pad">
        <h1 className="screen-title integration-title">Integrations</h1>
        <p className="integration-subtitle">Manage your connected services. Connected tools are made available to Hugin.</p>
        {loading ? <p className="integration-subtitle">Refreshing integration status…</p> : null}
        {!loading && error ? <p className="login-error">{error}</p> : null}
      </div>

      <div className="integrations-list">
        <div className="history-group-label">SERVICES</div>
        {integrations.length === 0 ? (
          <p className="history-empty">{loading ? "Refreshing integrations…" : "No integrations available."}</p>
        ) : (
          integrations.map((integration) => (
            <div key={integration.id} className="integration-card">
              <Puzzle size={26} strokeWidth={1.7} color="#1C1F23" />
              <div className="integration-copy">
                <div className="integration-name-row">
                  <span className="integration-name">{integration.name}</span>
                  {integration.connected ? <span className="integration-badge">CONNECTED</span> : null}
                </div>
                <div className="integration-meta">{integration.description}</div>
              </div>
              <div className="integration-action">
                {integration.reconnectable ? (
                  integration.connected ? (
                    <>
                      <button
                        type="button"
                        className="icon-button refresh-button"
                        disabled={busyId === integration.id}
                        onClick={() => onReconnect(integration)}
                        aria-label={`Refresh ${integration.name} connection`}
                        title="Reconnect to refresh permissions"
                      >
                        <RefreshCw size={18} strokeWidth={2} />
                      </button>
                      <button
                        type="button"
                        className="secondary-button"
                        disabled={busyId === integration.id}
                        onClick={() => onToggle(integration)}
                      >
                        {busyId === integration.id ? "…" : "Disconnect"}
                      </button>
                    </>
                  ) : (
                    <button
                      type="button"
                      className="dark-button"
                      disabled={busyId === integration.id}
                      onClick={() => onToggle(integration)}
                    >
                      {busyId === integration.id ? "Connecting…" : "Connect"}
                    </button>
                  )
                ) : (
                  <span className="integration-meta">{integration.connected ? "Active" : "Off"}</span>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </>
  );
}
