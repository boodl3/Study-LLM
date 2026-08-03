package com.studyllm.auth;

import com.studyllm.auth.dto.ChangePasswordRequest;
import com.studyllm.auth.dto.UpdateProfileRequest;
import com.studyllm.auth.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated self-service account management: profile updates and password changes. */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

  private final AuthService authService;

  public UserController(AuthService authService) {
    this.authService = authService;
  }

  @PatchMapping
  public UserDto updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
    return authService.updateProfile(request);
  }

  @PutMapping("/password")
  public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(request);
    return ResponseEntity.noContent().build();
  }
}
