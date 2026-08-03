import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { GlassCard } from "../components/GlassCard";
import { useAuth } from "./AuthContext";
import { ApiError } from "../api/client";
import "./AuthForm.css";

export function SignupPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }
    setSubmitting(true);
    try {
      await signup(username, email, password, confirmPassword);
      navigate("/");
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError(
          typeof err.body === "object" &&
            err.body !== null &&
            "message" in err.body &&
            typeof err.body.message === "string"
            ? err.body.message
            : "That username or email is already taken.",
        );
      } else if (err instanceof ApiError && err.status === 400) {
        setError("Check your username, email, and password (at least 8 characters).");
      } else {
        setError("Something went wrong.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-form-page">
      <GlassCard className="auth-form">
        <form onSubmit={handleSubmit} className="auth-form__fields">
          <h1>Sign Up</h1>
          {error && <div className="auth-form__error">{error}</div>}
          <input
            type="text"
            placeholder="Username"
            required
            minLength={3}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
          <input
            type="email"
            placeholder="Email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <input
            type="password"
            placeholder="Password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <input
            type="password"
            placeholder="Confirm password"
            required
            minLength={8}
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />
          <button type="submit" disabled={submitting}>
            {submitting ? "Creating account…" : "Create Account"}
          </button>
          <div className="auth-form__switch">
            Already have an account? <Link to="/login">Sign In</Link>
          </div>
        </form>
      </GlassCard>
    </div>
  );
}
