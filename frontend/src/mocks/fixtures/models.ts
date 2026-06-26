import type { ModelOption } from "../../lib/types";

const REASONING = ["none", "low", "medium", "high"];

/**
 * A realistic catalogue of OpenRouter-style models. The first three are enabled (so the composer
 * model picker and Settings show a populated list); the rest are available to toggle on. Prices and
 * context windows are illustrative, not live.
 */
export const mockModels: ModelOption[] = [
  {
    id: "anthropic/claude-opus-4.8",
    name: "Claude Opus 4.8",
    description: "Anthropic's most capable model for complex agentic coding and long-horizon reasoning.",
    contextLength: 200_000,
    promptPrice: "15",
    completionPrice: "75",
    reasoningOptions: REASONING,
    enabled: true
  },
  {
    id: "anthropic/claude-sonnet-4.6",
    name: "Claude Sonnet 4.6",
    description: "Balanced speed and quality — the default for everyday chats and quick edits.",
    contextLength: 200_000,
    promptPrice: "3",
    completionPrice: "15",
    reasoningOptions: REASONING,
    enabled: true
  },
  {
    id: "openai/gpt-4o",
    name: "GPT-4o",
    description: "OpenAI's multimodal flagship with strong general reasoning.",
    contextLength: 128_000,
    promptPrice: "2.5",
    completionPrice: "10",
    reasoningOptions: ["none"],
    enabled: true
  },
  {
    id: "google/gemini-2.5-pro",
    name: "Gemini 2.5 Pro",
    description: "Google's long-context model, useful for large repositories and documents.",
    contextLength: 1_000_000,
    promptPrice: "1.25",
    completionPrice: "10",
    reasoningOptions: ["none", "low", "medium", "high"],
    enabled: false
  },
  {
    id: "meta-llama/llama-3.3-70b-instruct",
    name: "Llama 3.3 70B Instruct",
    description: "Open-weight model with a strong price/performance ratio.",
    contextLength: 131_072,
    promptPrice: "0.6",
    completionPrice: "0.6",
    reasoningOptions: ["none"],
    enabled: false
  }
];
