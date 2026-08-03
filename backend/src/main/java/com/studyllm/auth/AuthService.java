package com.studyllm.auth;

import com.studyllm.auth.dto.AuthResponse;
import com.studyllm.auth.dto.ChangePasswordRequest;
import com.studyllm.auth.dto.LoginRequest;
import com.studyllm.auth.dto.SignupRequest;
import com.studyllm.auth.dto.UpdateProfileRequest;
import com.studyllm.auth.dto.UserDto;
import com.studyllm.common.ConflictException;
import com.studyllm.common.NotFoundException;
import com.studyllm.common.OwnershipGuard;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Signup, login, and account-management for the local username/password auth scheme. */
@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final OwnershipGuard ownershipGuard;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      OwnershipGuard ownershipGuard) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.ownershipGuard = ownershipGuard;
  }

  /** Creates a new account after checking password confirmation and username/email uniqueness, then issues a JWT. */
  public AuthResponse signup(SignupRequest request) {
    if (!request.password().equals(request.confirmPassword())) {
      throw new IllegalArgumentException("Passwords do not match");
    }
    if (userRepository.existsByUsername(request.username())) {
      throw new ConflictException("Username is already taken");
    }
    if (userRepository.existsByEmail(request.email())) {
      throw new ConflictException("Email is already registered");
    }
    User user =
        new User(request.username(), request.email(), passwordEncoder.encode(request.password()));
    userRepository.save(user);
    return new AuthResponse(
        user.getId(), user.getUsername(), user.getEmail(), jwtService.issueToken(user.getId()));
  }

  /** Verifies credentials (by username or email) and issues a JWT on success. */
  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByUsernameOrEmail(request.identifier(), request.identifier())
            .orElseThrow(() -> new BadCredentialsException("Invalid username/email or password"));
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new BadCredentialsException("Invalid email or password");
    }
    return new AuthResponse(
        user.getId(), user.getUsername(), user.getEmail(), jwtService.issueToken(user.getId()));
  }

  /** Updates the current user's username/email after checking the new values aren't taken. */
  @Transactional
  public UserDto updateProfile(UpdateProfileRequest request) {
    UUID userId = ownershipGuard.currentUserId();
    User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    if (userRepository.existsByUsernameAndIdNot(request.username(), userId)) {
      throw new ConflictException("Username is already taken");
    }
    if (userRepository.existsByEmailAndIdNot(request.email(), userId)) {
      throw new ConflictException("Email is already registered");
    }
    user.setUsername(request.username());
    user.setEmail(request.email());
    userRepository.save(user);
    return new UserDto(user.getId(), user.getUsername(), user.getEmail());
  }

  /** Changes the current user's password after verifying the current one and the new confirmation. */
  @Transactional
  public void changePassword(ChangePasswordRequest request) {
    if (!request.newPassword().equals(request.confirmNewPassword())) {
      throw new IllegalArgumentException("Passwords do not match");
    }
    User user =
        userRepository
            .findById(ownershipGuard.currentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new BadCredentialsException("Current password is incorrect");
    }
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
  }
}
