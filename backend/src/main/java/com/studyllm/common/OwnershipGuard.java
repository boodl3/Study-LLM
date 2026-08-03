package com.studyllm.common;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated caller's user id from the JWT-backed security context (spec
 * FR-019, FR-022). Every notebook/source/chat query must be scoped through this, never left to
 * the client.
 */
@Component
public class OwnershipGuard {

  public UUID currentUserId() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (!(principal instanceof UUID userId)) {
      throw new IllegalStateException("No authenticated user in security context");
    }
    return userId;
  }
}
