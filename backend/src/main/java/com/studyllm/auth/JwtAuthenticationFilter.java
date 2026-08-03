package com.studyllm.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-request filter that authenticates a {@code Bearer} JWT and populates the security context. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  /**
   * Extracts and validates the {@code Authorization: Bearer <token>} header, if present, and
   * sets the authenticated user ID as the security context's principal before continuing the
   * chain. Requests with no/invalid token simply proceed unauthenticated.
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      Optional<UUID> userId = jwtService.validateAndGetUserId(header.substring(7));
      userId.ifPresent(
          id -> {
            var authentication =
                new UsernamePasswordAuthenticationToken(id, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
          });
    }
    chain.doFilter(request, response);
  }
}
