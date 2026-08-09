package com.studyllm.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studyllm.common.OwnershipGuard;
import com.studyllm.notebook.Notebook;
import com.studyllm.notebook.NotebookRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

  private static final byte[] PDF_HEADER =
      "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes();
  private static final byte[] PNG_HEADER =
      new byte[] {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0
      };

  @Mock private NotebookRepository notebookRepository;
  @Mock private SourceRepository sourceRepository;
  @Mock private SourceIngestionPipeline ingestionPipeline;
  @Mock private OwnershipGuard ownershipGuard;

  private SourceService sourceService;
  private final UUID userId = UUID.randomUUID();
  private final UUID notebookId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    sourceService =
        new SourceService(notebookRepository, sourceRepository, ingestionPipeline, ownershipGuard, 50L);
    lenient().when(ownershipGuard.currentUserId()).thenReturn(userId);
    lenient()
        .when(notebookRepository.findByIdAndOwnerId(notebookId, userId))
        .thenReturn(Optional.of(new Notebook(userId, "Bio 101")));
  }

  @Test
  void upload_rejectsOversizedFile() {
    // maxFileSizeBytes is 50 in this test; send more than that.
    byte[] tooBig = new byte[51];
    var file = new MockMultipartFile("file", "big.pdf", "application/pdf", tooBig);

    assertThatThrownBy(() -> sourceService.upload(notebookId, file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("50MB");

    verify(sourceRepository, never()).save(org.mockito.ArgumentMatchers.any());
    verify(ingestionPipeline, never())
        .process(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void upload_rejectsUnsupportedFileType() {
    var file = new MockMultipartFile("file", "image.png", "image/png", PNG_HEADER);

    assertThatThrownBy(() -> sourceService.upload(notebookId, file))
        .isInstanceOf(IllegalArgumentException.class);

    verify(sourceRepository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void upload_ignoresDeclaredExtensionAndSniffsRealContent() {
    // Filename claims .pdf but the actual bytes are a PNG — content-sniffing must win.
    var file = new MockMultipartFile("file", "notes.pdf", "application/pdf", PNG_HEADER);

    assertThatThrownBy(() -> sourceService.upload(notebookId, file))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void upload_acceptsRealPdfContentAndQueuesProcessing() {
    when(sourceRepository.save(org.mockito.ArgumentMatchers.any(Source.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    var file = new MockMultipartFile("file", "syllabus.pdf", "application/pdf", PDF_HEADER);

    var response = sourceService.upload(notebookId, file);

    assertThat(response.status()).isEqualTo(Source.ProcessingStatus.PROCESSING);
    verify(ingestionPipeline)
        .process(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq(PDF_HEADER),
            org.mockito.ArgumentMatchers.eq(Source.FileType.PDF));
  }

  @Test
  void reorder_appliesRequestedOrderAndAppendsUnlistedSourcesAtEnd() {
    Source a = new Source(notebookId, "a.txt", Source.FileType.TXT, 1);
    Source b = new Source(notebookId, "b.txt", Source.FileType.TXT, 1);
    Source c = new Source(notebookId, "c.txt", Source.FileType.TXT, 1);
    UUID aId = UUID.randomUUID();
    UUID bId = UUID.randomUUID();
    UUID cId = UUID.randomUUID();
    ReflectionTestUtils.setField(a, "id", aId);
    ReflectionTestUtils.setField(b, "id", bId);
    ReflectionTestUtils.setField(c, "id", cId);
    when(sourceRepository.findByNotebookIdOrderBySortOrderAscUploadedAtAsc(notebookId))
        .thenReturn(List.of(a, b, c));

    // b is left out of the requested order, so it should be appended after c and a.
    sourceService.reorder(notebookId, List.of(cId, aId));

    assertThat(c.getSortOrder()).isZero();
    assertThat(a.getSortOrder()).isEqualTo(1);
    assertThat(b.getSortOrder()).isEqualTo(2);
    verify(sourceRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
  }
}
