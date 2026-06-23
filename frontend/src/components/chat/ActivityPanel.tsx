import type { ChatActivity } from "../../lib/types";

/**
 * Renders the low-level activity projection for a thread: tool calls and their output, run
 * lifecycle, workspace/GitHub progress, and any other status/debug events. The main chat stays
 * limited to natural-language messages; everything else surfaces here.
 */
export function ActivityPanel({ activities }: { activities: ChatActivity[] }) {
  if (!activities.length) return null;
  return (
    <details className="activity-panel" open>
      <summary className="activity-header">Activity</summary>
      <div className="activity-list">
        {activities.map((activity) => (
          <div key={activity.id} className="activity-item">
            <div className="activity-item-head">
              <span className={`activity-status activity-status-${activity.status}`} />
              <span>{activity.label}</span>
            </div>
            {activity.detail ? <pre className="activity-detail">{activity.detail}</pre> : null}
          </div>
        ))}
      </div>
    </details>
  );
}
