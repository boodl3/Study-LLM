import { apiClient } from "./client";

export interface AuthResponse {
  userId: string;
  username: string;
  email: string;
  token: string;
}

export function signup(
  username: string,
  email: string,
  password: string,
  confirmPassword: string,
) {
  return apiClient.post<AuthResponse>("/auth/signup", {
    username,
    email,
    password,
    confirmPassword,
  });
}

export function login(identifier: string, password: string) {
  return apiClient.post<AuthResponse>("/auth/login", { identifier, password });
}

export interface UserProfile {
  userId: string;
  username: string;
  email: string;
}

export function updateProfile(username: string, email: string) {
  return apiClient.patch<UserProfile>("/users/me", { username, email });
}

export function changePassword(
  currentPassword: string,
  newPassword: string,
  confirmNewPassword: string,
) {
  return apiClient.put<void>("/users/me/password", {
    currentPassword,
    newPassword,
    confirmNewPassword,
  });
}
