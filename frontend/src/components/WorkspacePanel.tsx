import { useState } from "react";
import { ChevronDown, ChevronRight, FileText, Folder, FolderOpen, Network } from "lucide-react";

import type { FileNode } from "../lib/types";
import { COLORS } from "../lib/theme";
import { formatBytes } from "../lib/format";

function TreeRow({
  depth = 0,
  onClick,
  children
}: {
  depth?: number;
  onClick?: () => void;
  children: React.ReactNode;
}) {
  return (
    <div
      className={`tree-row ${onClick ? "tree-row-clickable" : ""}`}
      style={{ paddingLeft: depth * 16 }}
      onClick={onClick}
    >
      {children}
    </div>
  );
}

function FileNodeRow({ node, depth, defaultOpen }: { node: FileNode; depth: number; defaultOpen: boolean }) {
  const [open, setOpen] = useState(defaultOpen);

  if (node.type === "dir") {
    return (
      <>
        <TreeRow depth={depth} onClick={() => setOpen((current) => !current)}>
          {open ? <ChevronDown size={13} color={COLORS.faint} /> : <ChevronRight size={13} color={COLORS.faint} />}
          {open ? (
            <FolderOpen size={14} strokeWidth={2} color={COLORS.ink} />
          ) : (
            <Folder size={14} strokeWidth={2} color={COLORS.ink} />
          )}
          <span>{node.name}</span>
        </TreeRow>
        {open ? node.children?.map((child) => (
          <FileNodeRow key={child.path} node={child} depth={depth + 1} defaultOpen={defaultOpen} />
        )) : null}
      </>
    );
  }

  return (
    <TreeRow depth={depth}>
      <FileText size={13.5} strokeWidth={2} color={COLORS.muted} />
      <span className="mono">{node.name}</span>
      <span className="tree-size mono">{formatBytes(node.size)}</span>
    </TreeRow>
  );
}

/**
 * Maps a project chat's raw sandbox container status to a user-facing badge. The container id is
 * intentionally never exposed — only its health (Sandbox Ready / Starting / Failed).
 */
function describeSandboxStatus(status?: string): { label: string; color: string } | null {
  switch (status) {
    case "running":
      return { label: "Sandbox Ready", color: COLORS.ink };
    case "stopped":
      return { label: "Sandbox Starting", color: COLORS.muted };
    case "error":
      return { label: "Sandbox Failed", color: "#c0392b" };
    default:
      return null;
  }
}

/** Sandbox / GitHub repository file tree shown alongside workspace-backed chats. */
export function WorkspacePanel(props: {
  sessionId: string;
  files: FileNode[];
  wsOpen: boolean;
  onToggleWs: () => void;
  label: string;
  rootName: string;
  badge: string;
  /** Raw container status for a project chat's sandbox ("running" | "stopped" | "error"); omitted otherwise. */
  sandboxStatus?: string;
  defaultOpenDirectories: boolean;
}) {
  const { files, wsOpen, onToggleWs, label, rootName, badge, sandboxStatus, defaultOpenDirectories } = props;
  const sandboxBadge = describeSandboxStatus(sandboxStatus);

  return (
    <div className="file-tree">
      <TreeRow>
        <ChevronDown size={13} color={COLORS.faint} />
        <Network size={14} strokeWidth={2} color={COLORS.ink} />
        <span className="mono">{label || "~/"}</span>
        <span className="tree-badge">{badge}</span>
        {sandboxBadge ? (
          <span className="tree-badge" style={{ color: sandboxBadge.color }} title="Isolated sandbox container health">
            {sandboxBadge.label}
          </span>
        ) : null}
      </TreeRow>

      <TreeRow depth={1} onClick={onToggleWs}>
        {wsOpen ? <ChevronDown size={13} color={COLORS.faint} /> : <ChevronRight size={13} color={COLORS.faint} />}
        {wsOpen ? (
          <FolderOpen size={14} strokeWidth={2} color={COLORS.ink} />
        ) : (
          <Folder size={14} strokeWidth={2} color={COLORS.ink} />
        )}
        <span>{rootName}</span>
      </TreeRow>

      {wsOpen ? (
        files.length ? (
          files.map((node) => <FileNodeRow key={node.path} node={node} depth={2} defaultOpen={defaultOpenDirectories} />)
        ) : (
          <TreeRow depth={2}>
            <span className="mono" style={{ color: COLORS.faint }}>
              (empty)
            </span>
          </TreeRow>
        )
      ) : null}
    </div>
  );
}
