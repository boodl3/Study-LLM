# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# Cache mount keeps ~/.m2 warm across builds without baking ~300MB of jars into a layer.
# -DskipTests: surefire is unconfigured, so `package` would otherwise run the unit tests.
# (Failsafe's *IT tests bind to verify, after package, so they're already excluded — and they
# need a Docker daemon, which isn't available inside a build.)
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:21-jre-noble

# tesseract-ocr(+eng): Tess4J ships Windows-only natives, so on Linux JNA loads the system
#   libtesseract. libtesseract-dev supplies the unversioned libtesseract.so symlink that
#   JNA's Native.load("tesseract") resolves — Debian otherwise ships only libtesseract.so.5.
# fontconfig + fonts-dejavu-core + libfreetype6: PdfTextExtractor rasterizes pages via
#   PDFBox/java.awt, which needs a real font stack even headless.
# curl: for HEALTHCHECK — a JRE image has no command-line HTTP client.
#
# The three checks at the end fail the build rather than let OCR degrade silently at runtime:
# OcrClient catches UnsatisfiedLinkError and falls back to the vision model, so a broken
# native link would otherwise ship as a working-but-25x-slower image.
RUN apt-get update && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-eng \
        libtesseract-dev \
        fontconfig \
        fonts-dejavu-core \
        libfreetype6 \
        curl \
    && rm -rf /var/lib/apt/lists/* \
    && ldconfig -p | grep -q 'libtesseract\.so ' \
    && test -f /usr/share/tesseract-ocr/5/tessdata/eng.traineddata \
    && tesseract --list-langs

# Baked into the image because the path is a property of the base image, not the deployment.
ENV STUDYLLM_TESSDATA_PATH=/usr/share/tesseract-ocr/5/tessdata

# logs/ is created and owned up front: logback-spring.xml adds a RollingFileAppender writing to
# a path relative to WORKDIR, and the app user cannot create it under a root-owned /app.
# Console is the primary channel in a container; the file appender lives in the container's
# writable layer and is discarded with it.
RUN useradd --system --uid 1001 --create-home --shell /usr/sbin/nologin app \
    && mkdir -p /app/logs \
    && chown -R app:app /app
USER app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/study-llm-backend-*.jar app.jar

EXPOSE 8080
# /actuator/health exactly — SecurityConfig permits that literal path, and the
# readiness/liveness sub-paths are neither exposed nor permitted.
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# MaxRAMPercentage: the JVM's default 25% heap cap wastes most of a memory-limited container.
# ExitOnOutOfMemoryError: OCR and embedding batches can OOM; a half-dead JVM still answering
# the health check is worse than a restart.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
