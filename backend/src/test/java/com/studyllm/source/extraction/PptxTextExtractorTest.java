package com.studyllm.source.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.studyllm.ai.OcrClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PptxTextExtractorTest {

  @Mock private OcrClient ocrClient;

  private PptxTextExtractor extractor;

  @BeforeEach
  void setUp() {
    extractor = new PptxTextExtractor(ocrClient);
  }

  @Test
  void foldsTheBatchedOcrResultIntoItsOwnSlideSection() throws IOException {
    when(ocrClient.extractTextBatch(any())).thenReturn(List.of("scanned text via OCR"));

    List<ExtractedSection> sections = extractor.extract(new ByteArrayInputStream(pptx("Hello")));

    assertThat(sections).hasSize(1);
    assertThat(sections.get(0).label()).isEqualTo("slide 1");
    assertThat(sections.get(0).text()).contains("Hello").contains("scanned text via OCR");
  }

  private byte[] pptx(String text) throws IOException {
    try (XMLSlideShow ppt = new XMLSlideShow()) {
      XSLFSlide slide = ppt.createSlide();
      XSLFTextBox textBox = slide.createTextBox();
      textBox.setText(text);

      BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
      ByteArrayOutputStream imageOut = new ByteArrayOutputStream();
      ImageIO.write(image, "png", imageOut);
      XSLFPictureData pictureData =
          ppt.addPicture(imageOut.toByteArray(), PictureData.PictureType.PNG);
      slide.createPicture(pictureData);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ppt.write(out);
      return out.toByteArray();
    }
  }
}
