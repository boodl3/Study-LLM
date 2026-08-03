package com.studyllm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studyllm.auth.dto.ChangePasswordRequest;
import com.studyllm.auth.dto.LoginRequest;
import com.studyllm.auth.dto.SignupRequest;
import com.studyllm.auth.dto.UpdateProfileRequest;
import com.studyllm.common.ConflictException;
import com.studyllm.common.OwnershipGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private OwnershipGuard ownershipGuard;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(userRepository, passwordEncoder, jwtService, ownershipGuard);
    lenient().when(userRepository.existsByUsername(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
  }

  @Test
  void signup_rejectsMismatchedPasswords() {
    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest("newuser", "new@example.com", "password123", "different123")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void signup_rejectsTakenUsername() {
    when(userRepository.existsByUsername("taken")).thenReturn(true);

    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest("taken", "new@example.com", "password123", "password123")))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void signup_rejectsDuplicateEmail() {
    when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                authService.signup(
                    new SignupRequest(
                        "newuser", "taken@example.com", "password123", "password123")))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void signup_hashesPasswordAndIssuesToken() {
    when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("hashed");
    when(jwtService.issueToken(any())).thenReturn("jwt-token");

    var response =
        authService.signup(
            new SignupRequest("newuser", "new@example.com", "password123", "password123"));

    assertThat(response.username()).isEqualTo("newuser");
    assertThat(response.email()).isEqualTo("new@example.com");
    assertThat(response.token()).isEqualTo("jwt-token");
    verify(userRepository).save(any(User.class));
  }

  @Test
  void login_rejectsInvalidCredentials() {
    when(userRepository.findByUsernameOrEmail("nobody@example.com", "nobody@example.com"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("nobody@example.com", "password123")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void login_rejectsWrongPassword() {
    User user = new User("someuser", "user@example.com", "hashed");
    when(userRepository.findByUsernameOrEmail("user@example.com", "user@example.com"))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

    assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void login_acceptsUsernameAsIdentifier() {
    User user = new User("someuser", "user@example.com", "hashed");
    when(userRepository.findByUsernameOrEmail("someuser", "someuser")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
    when(jwtService.issueToken(any())).thenReturn("jwt-token");

    var response = authService.login(new LoginRequest("someuser", "password123"));

    assertThat(response.username()).isEqualTo("someuser");
  }

  @Test
  void updateProfile_rejectsTakenUsername() {
    UUID userId = UUID.randomUUID();
    when(ownershipGuard.currentUserId()).thenReturn(userId);
    when(userRepository.findById(userId))
        .thenReturn(Optional.of(new User("olduser", "old@example.com", "hashed")));
    when(userRepository.existsByUsernameAndIdNot("taken", userId)).thenReturn(true);

    assertThatThrownBy(
            () -> authService.updateProfile(new UpdateProfileRequest("taken", "old@example.com")))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void updateProfile_updatesUsernameAndEmail() {
    UUID userId = UUID.randomUUID();
    when(ownershipGuard.currentUserId()).thenReturn(userId);
    User user = new User("olduser", "old@example.com", "hashed");
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    var response =
        authService.updateProfile(new UpdateProfileRequest("newuser", "new@example.com"));

    assertThat(response.username()).isEqualTo("newuser");
    assertThat(response.email()).isEqualTo("new@example.com");
    verify(userRepository).save(user);
  }

  @Test
  void changePassword_rejectsMismatchedNewPasswords() {
    assertThatThrownBy(
            () ->
                authService.changePassword(
                    new ChangePasswordRequest("oldpass123", "newpass123", "different123")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changePassword_rejectsWrongCurrentPassword() {
    UUID userId = UUID.randomUUID();
    when(ownershipGuard.currentUserId()).thenReturn(userId);
    when(userRepository.findById(userId))
        .thenReturn(Optional.of(new User("someuser", "user@example.com", "hashed")));
    when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

    assertThatThrownBy(
            () ->
                authService.changePassword(
                    new ChangePasswordRequest("wrong", "newpass123", "newpass123")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void changePassword_hashesAndSavesNewPassword() {
    UUID userId = UUID.randomUUID();
    when(ownershipGuard.currentUserId()).thenReturn(userId);
    User user = new User("someuser", "user@example.com", "oldHashed");
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("oldpass123", "oldHashed")).thenReturn(true);
    when(passwordEncoder.encode("newpass123")).thenReturn("newHashed");

    authService.changePassword(new ChangePasswordRequest("oldpass123", "newpass123", "newpass123"));

    assertThat(user.getPasswordHash()).isEqualTo("newHashed");
    verify(userRepository).save(user);
  }
}
