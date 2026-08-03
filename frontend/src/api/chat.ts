import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "./client";

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

export function useAskQuestion(notebookId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (question: string) =>
      apiClient.post<ChatMessage>(`/notebooks/${notebookId}/chat`, { question }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: chatHistoryKey(notebookId) });
    },
  });
}
