import type { FileNode } from "../../lib/types";

/**
 * A realistic project file tree for the Project-chat workspace panel. Mirrors a small Vite/React
 * repository so the panel renders folders and files the way a reviewer would expect.
 */
export const mockProjectFiles: FileNode[] = [
  {
    name: "src",
    path: "src",
    type: "dir",
    children: [
      {
        name: "components",
        path: "src/components",
        type: "dir",
        children: [
          { name: "App.tsx", path: "src/components/App.tsx", type: "file", size: 4213 },
          { name: "Header.tsx", path: "src/components/Header.tsx", type: "file", size: 1820 },
          { name: "OnboardingWizard.tsx", path: "src/components/OnboardingWizard.tsx", type: "file", size: 6104 }
        ]
      },
      {
        name: "lib",
        path: "src/lib",
        type: "dir",
        children: [
          { name: "api.ts", path: "src/lib/api.ts", type: "file", size: 2310 },
          { name: "format.ts", path: "src/lib/format.ts", type: "file", size: 980 }
        ]
      },
      { name: "main.tsx", path: "src/main.tsx", type: "file", size: 642 },
      { name: "styles.css", path: "src/styles.css", type: "file", size: 5821 }
    ]
  },
  {
    name: "tests",
    path: "tests",
    type: "dir",
    children: [
      { name: "onboarding.test.ts", path: "tests/onboarding.test.ts", type: "file", size: 3100 }
    ]
  },
  { name: "package.json", path: "package.json", type: "file", size: 1204 },
  { name: "README.md", path: "README.md", type: "file", size: 2456 },
  { name: "vite.config.ts", path: "vite.config.ts", type: "file", size: 410 }
];

/** Server home directory (~/) tree backing Agent-mode chats. */
export const mockAgentFiles: FileNode[] = [
  {
    name: "notes",
    path: "notes",
    type: "dir",
    children: [
      { name: "research-summary.md", path: "notes/research-summary.md", type: "file", size: 1840 },
      { name: "todo.md", path: "notes/todo.md", type: "file", size: 512 }
    ]
  },
  { name: "report.md", path: "report.md", type: "file", size: 3120 }
];
