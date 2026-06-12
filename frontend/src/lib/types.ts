export type AuthSession = {
  token: string;
  username: string;
  roles: string[];
  expiresAt: string;
};

export type StreamToolEvent = {
  id: string;
  name: string;
  args: string;
  result: string;
  startedAt: string;
  finishedAt?: string;
};

export type ChatEntry =
  | {
      id: string;
      type: "user";
      content: string;
      createdAt: string;
    }
  | {
      id: string;
      type: "assistant";
      content: string;
      reasoning: string;
      createdAt: string;
      completedAt?: string;
    }
  | {
      id: string;
      type: "tool";
      tool: StreamToolEvent;
      createdAt: string;
    };

export type ChatThread = {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  entries: ChatEntry[];
};

export type AppState = {
  threads: ChatThread[];
};
