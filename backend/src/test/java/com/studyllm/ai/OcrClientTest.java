package com.studyllm.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class OcrClientTest {

  @Test
  void downscalesAnOversizedImagePreservingAspectRatio() throws IOException {
    BufferedImage scaled = readImage(OcrClient.downscale(png(2000, 1000)));

    assertThat(scaled.getWidth()).isEqualTo(1024);
    assertThat(scaled.getHeight()).isEqualTo(512);
  }

  @Test
  void leavesAnAlreadySmallImageByteForByteUntouched() throws IOException {
    byte[] original = png(800, 600);

    assertThat(OcrClient.downscale(original)).isSameAs(original);
  }

  @Test
  void returnsTheOriginalBytesWhenTheImageCannotBeDecoded() {
    byte[] notAnImage = "this is not an image".getBytes();

    assertThat(OcrClient.downscale(notAnImage)).isSameAs(notAnImage);
  }

  private static byte[] png(int width, int height) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  private static BufferedImage readImage(byte[] bytes) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(bytes));
  }

  @Test
  void stripsPreambleBeforeTheMarker() {
    String raw =
        "The image contains a screenshot of an educational slide. "
            + "===TEXT===Set Theory\nChapter 6";

    assertThat(OcrClient.extractAfterMarker(raw)).isEqualTo("Set Theory\nChapter 6");
  }

  @Test
  void returnsEmptyWhenMarkerIsFollowedByNothing() {
    assertThat(OcrClient.extractAfterMarker("===TEXT===")).isEmpty();
  }

  @Test
  void fallsBackToRawTextWhenTheModelOmitsTheMarker() {
    assertThat(OcrClient.extractAfterMarker(" Set Theory ")).isEqualTo("Set Theory");
  }

  @Test
  void returnsEmptyForANullResponse() {
    assertThat(OcrClient.extractAfterMarker(null)).isEmpty();
  }

  @Test
  void truncatesAtAnEchoedClosingMarker() {
    String raw = "===TEXT===\n\nDefinition: two sets are equal...\n\n===TEXT===\"";

    assertThat(OcrClient.extractAfterMarker(raw)).isEqualTo("Definition: two sets are equal...");
  }
}
