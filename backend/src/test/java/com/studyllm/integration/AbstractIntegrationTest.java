package com.studyllm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyllm.ai.EmbeddingClient;
import com.studyllm.auth.JwtService;
import com.studyllm.auth.User;
import com.studyllm.auth.UserRepository;
import com.studyllm.chat.ChatMessageRepository;
import com.studyllm.notebook.NotebookRepository;
import com.studyllm.source.ChunkRepository;
import com.studyllm.source.SourceRepository;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers (Postgres + pgvector) and MockMvc/JWT plumbing for backend integration
 * tests (constitution Article V — one Postgres container per JVM run, reused across subclasses).
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("studyllm")
          .withUsername("studyllm")
          .withPassword("studyllm");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected UserRepository userRepository;
  @Autowired protected JwtService jwtService;
  @Autowired protected NotebookRepository notebookRepository;
  @Autowired protected SourceRepository sourceRepository;
  @Autowired protected ChunkRepository chunkRepository;
  @Autowired protected ChatMessageRepository chatMessageRepository;
  @Autowired protected EmbeddingClient embeddingClient;

  protected UUID createUserAndGetId() {
    String suffix = UUID.randomUUID().toString();
    User user =
        userRepository.save(new User("user-" + suffix, "user-" + suffix + "@example.com", "hashed"));
    return user.getId();
  }

  protected String tokenFor(UUID userId) {
    return jwtService.issueToken(userId);
  }
}
