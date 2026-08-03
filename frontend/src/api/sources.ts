import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "./client";

export type SourceStatus = "PROCESSING" | "READY" | "FAILED";

export interface Source {
  id: string;
  filename: string;
  fileType: "PDF" | "DOCX" | "PPTX" | "TXT" | "MD";
  status: SourceStatus;
  failureReason: string | null;
  uploadedAt: string;
}

interface SourceListResponse {
  sources: Source[];
}

function sourcesKey(notebookId: string) {
  return ["notebooks", notebookId, "sources"];
}

export function useSources(notebookId: string) {
  return useQuery({
    queryKey: sourcesKey(notebookId),
    queryFn: () => apiClient.get<SourceListResponse>(`/notebooks/${notebookId}/sources`),
    select: (data) => data.sources,
    // Poll while any source is still processing, so status updates land without a manual refresh.
    refetchInterval: (query) => {
      const sources = query.state.data?.sources;
      return sources?.some((s) => s.status === "PROCESSING") ? 2000 : false;
    },
  });
}

export function useUploadSource(notebookId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData();
      formData.append("file", file);
      return apiClient.post(`/notebooks/${notebookId}/sources`, formData);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: sourcesKey(notebookId) }),
  });
}

export function useRenameSource(notebookId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, filename }: { id: string; filename: string }) =>
      apiClient.patch<Source>(`/notebooks/${notebookId}/sources/${id}`, { filename }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: sourcesKey(notebookId) }),
  });
}

export function useDeleteSource(notebookId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiClient.delete<void>(`/notebooks/${notebookId}/sources/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: sourcesKey(notebookId) }),
  });
}
