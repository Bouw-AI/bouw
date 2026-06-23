import type { ModelOption } from "./types";

export function formatBytes(size?: number) {
  if (size == null) return "";
  if (size < 1024) return `${size} b`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} kb`;
  return `${(size / (1024 * 1024)).toFixed(1)} mb`;
}

export function labelReasoning(value: string) {
  return value === "none" ? "Off" : value.charAt(0).toUpperCase() + value.slice(1);
}

export function defaultReasoningFor(model?: ModelOption) {
  if (!model || !model.reasoningOptions.length) return undefined;
  return model.reasoningOptions.includes("medium") ? "medium" : model.reasoningOptions[0];
}
