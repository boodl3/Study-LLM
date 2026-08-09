import { useCallback, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, ApiError, getToken } from "./client";

export interface CitedSource {
  sourceId: string;
  filename: string;
  sectionLabel: string | null;
}

export interface ChatMessage {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  citedSources: CitedSource[];
  notFoundInSources: boolean;
  createdAt: string;
}

interface ChatHistoryResponse {
  messages: ChatMessage[];
}

function chatHistoryKey(notebookId: string) {
  return ["notebooks", notebookId, "chat"];
}

export function useChatHistory(notebookId: string) {
  return useQuery({
    queryKey: chatHistoryKey(notebookId),
    queryFn: () =>
      apiClient.get<ChatHistoryResponse>(`/notebooks/${notebookId}/chat`),
    select: (data) => data.messages,
  });
}

// The chat endpoint streams Server-Sent Events (one `data: {"token": "..."}` line per piece of
// generated text, then a final `data: {"done": true, "message": {...}}`) so the answer renders as
// it's generated instead of only appearing once the (multi-minute, on local hardware) generation
// finishes. Parses the SSE framing directly since this is a POST body, not an EventSource.
async function askQuestionStreaming(
  notebookId: string,
  question: string,
  onToken: (token: string) => void,
): Promise<ChatMessage> {
  const token = getToken();
  const headers = new Headers({ "Content-Type": "application/json" });
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const res = await fetch(`/api/v1/notebooks/${notebookId}/chat`, {
    method: "POST",
    headers,
    body: JSON.stringify({ question }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new ApiError(res.status, body);
  }
  if (!res.body) {
    throw new Error("This browser doesn't support streamed responses.");
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let boundary = buffer.indexOf("\n\n");
    while (boundary !== -1) {
      const rawEvent = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      boundary = buffer.indexOf("\n\n");

      const dataLine = rawEvent.split("\n").find((line) => line.startsWith("data:"));
      if (!dataLine) continue;
      const payload = JSON.parse(dataLine.slice(5).trim());

      if (payload.error) {
        throw new Error(payload.message ?? "Failed to generate an answer.");
      }
      if (payload.done) {
        return payload.message as ChatMessage;
      }
      if (typeof payload.token === "string") {
        onToken(payload.token);
      }
    }
  }
  throw new Error("Stream ended without a final message.");
}

/**
 * Drives the chat flow for one notebook: streams the answer token-by-token into
 * `streamingAnswer` while `pendingQuestion` is set, then swaps to the authoritative persisted
 * message (with citations) once the stream completes.
 */
export function useStreamingChat(notebookId: string) {
  const queryClient = useQueryClient();
  const [pendingQuestion, setPendingQuestion] = useState<string | null>(null);
  const [streamingAnswer, setStreamingAnswer] = useState("");
  const [isAsking, setIsAsking] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const ask = useCallback(
    async (question: string) => {
      setError(null);
      setPendingQuestion(question);
      setStreamingAnswer("");
      setIsAsking(true);
      try {
        await askQuestionStreaming(notebookId, question, (token) =>
          setStreamingAnswer((prev) => prev + token),
        );
        await queryClient.invalidateQueries({ queryKey: chatHistoryKey(notebookId) });
      } catch (err) {
        setError(err instanceof Error ? err : new Error("Unknown error"));
      } finally {
        setPendingQuestion(null);
        setStreamingAnswer("");
        setIsAsking(false);
      }
    },
    [notebookId, queryClient],
  );

  return { ask, isAsking, pendingQuestion, streamingAnswer, error };
}
