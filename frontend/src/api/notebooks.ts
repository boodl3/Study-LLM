import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "./client";

export interface Notebook {
  id: string;
  title: string;
  sourceCount: number;
  lastActiveAt: string;
}

interface NotebookListResponse {
  notebooks: Notebook[];
}

const notebooksKey = ["notebooks"];

export function useNotebooks() {
  return useQuery({
    queryKey: notebooksKey,
    queryFn: () => apiClient.get<NotebookListResponse>("/notebooks"),
    select: (data) => data.notebooks,
  });
}

export function useNotebookSearch(query: string) {
  return useQuery({
    queryKey: ["notebooks", "search", query],
    queryFn: () =>
      apiClient.get<NotebookListResponse>(`/notebooks/search?q=${encodeURIComponent(query)}`),
    select: (data) => data.notebooks,
    enabled: query.trim().length > 0,
  });
}

export function useNotebook(id: string) {
  return useQuery({
    queryKey: ["notebooks", id],
    queryFn: () => apiClient.get<Notebook>(`/notebooks/${id}`),
  });
}

export function useCreateNotebook() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (title: string) => apiClient.post<Notebook>("/notebooks", { title }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notebooksKey }),
  });
}

export function useRenameNotebook() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) =>
      apiClient.patch<Notebook>(`/notebooks/${id}`, { title }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notebooksKey }),
  });
}

export function useDeleteNotebook() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.delete<void>(`/notebooks/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notebooksKey }),
  });
}
