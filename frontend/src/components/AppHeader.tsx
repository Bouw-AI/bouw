import { Bug, Menu } from "lucide-react";

const LOGO = "/hugin-bird.jpg";

/** Branded top bar with the menu trigger and the optional "Report bug" action. */
export function AppHeader({
  onMenu,
  reportAction
}: {
  onMenu: () => void;
  reportAction?: {
    disabled?: boolean;
    busy?: boolean;
    onClick: () => void;
  };
}) {
  return (
    <div className="app-header">
      <div className="brand">
        <img src={LOGO} alt="Hugin" className="brand-logo" />
      </div>
      <div className="header-actions">
        {reportAction ? (
          <button
            type="button"
            className="header-action-button"
            onClick={reportAction.onClick}
            disabled={reportAction.disabled || reportAction.busy}
            aria-label="Report bug"
          >
            <Bug size={14} strokeWidth={2} />
            <span>{reportAction.busy ? "Saving…" : "Report bug"}</span>
          </button>
        ) : null}
        <button type="button" className="icon-button" onClick={onMenu} aria-label="Open menu">
          <Menu size={22} strokeWidth={2} />
        </button>
      </div>
    </div>
  );
}
