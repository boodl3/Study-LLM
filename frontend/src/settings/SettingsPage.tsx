import { useState, type FormEvent } from "react";
import { GlassCard } from "../components/GlassCard";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";
import "../auth/AuthForm.css";
import "./SettingsPage.css";

export function SettingsPage() {
  const { username, email, updateProfile, changePassword } = useAuth();

  const [profileUsername, setProfileUsername] = useState(username ?? "");
  const [profileEmail, setProfileEmail] = useState(email ?? "");
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileSuccess, setProfileSuccess] = useState(false);
  const [profileSubmitting, setProfileSubmitting] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmNewPassword, setConfirmNewPassword] = useState("");
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState(false);
  const [passwordSubmitting, setPasswordSubmitting] = useState(false);

  async function handleProfileSubmit(e: FormEvent) {
    e.preventDefault();
    setProfileError(null);
    setProfileSuccess(false);
    setProfileSubmitting(true);
    try {
      await updateProfile(profileUsername, profileEmail);
      setProfileSuccess(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setProfileError("That username or email is already taken.");
      } else if (err instanceof ApiError && err.status === 400) {
        setProfileError("Check your username and email.");
      } else {
        setProfileError("Something went wrong.");
      }
    } finally {
      setProfileSubmitting(false);
    }
  }

  async function handlePasswordSubmit(e: FormEvent) {
    e.preventDefault();
    setPasswordError(null);
    setPasswordSuccess(false);
    if (newPassword !== confirmNewPassword) {
      setPasswordError("New passwords do not match.");
      return;
    }
    setPasswordSubmitting(true);
    try {
      await changePassword(currentPassword, newPassword, confirmNewPassword);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmNewPassword("");
      setPasswordSuccess(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setPasswordError("Current password is incorrect.");
      } else if (err instanceof ApiError && err.status === 400) {
        setPasswordError("New password must be at least 8 characters.");
      } else {
        setPasswordError("Something went wrong.");
      }
    } finally {
      setPasswordSubmitting(false);
    }
  }

  return (
    <div className="settings-page">
      <h1>Settings</h1>

      <GlassCard className="auth-form settings-page__section">
        <form onSubmit={handleProfileSubmit} className="auth-form__fields">
          <h2>Profile</h2>
          {profileError && <div className="auth-form__error">{profileError}</div>}
          {profileSuccess && <div className="settings-page__success">Profile updated.</div>}
          <input
            type="text"
            placeholder="Username"
            required
            minLength={3}
            value={profileUsername}
            onChange={(e) => setProfileUsername(e.target.value)}
          />
          <input
            type="email"
            placeholder="Email"
            required
            value={profileEmail}
            onChange={(e) => setProfileEmail(e.target.value)}
          />
          <button type="submit" disabled={profileSubmitting}>
            {profileSubmitting ? "Updating…" : "Update"}
          </button>
        </form>
      </GlassCard>

      <GlassCard className="auth-form settings-page__section">
        <form onSubmit={handlePasswordSubmit} className="auth-form__fields">
          <h2>Change Password</h2>
          {passwordError && <div className="auth-form__error">{passwordError}</div>}
          {passwordSuccess && <div className="settings-page__success">Password updated.</div>}
          <input
            type="password"
            placeholder="Current password"
            required
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
          />
          <input
            type="password"
            placeholder="New password"
            required
            minLength={8}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
          <input
            type="password"
            placeholder="Confirm new password"
            required
            minLength={8}
            value={confirmNewPassword}
            onChange={(e) => setConfirmNewPassword(e.target.value)}
          />
          <button type="submit" disabled={passwordSubmitting}>
            {passwordSubmitting ? "Updating…" : "Update"}
          </button>
        </form>
      </GlassCard>
    </div>
  );
}
