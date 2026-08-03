import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { GlassCard } from "../components/GlassCard";
import { useAuth } from "./AuthContext";
import { ApiError } from "../api/client";
import "./AuthForm.css";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(identifier, password);
      navigate("/");
    } catch (err) {
      setError(
        err instanceof ApiError ? "Invalid username/email or password." : "Something went wrong.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-form-page">
      <GlassCard className="auth-form">
        <form onSubmit={handleSubmit} className="auth-form__fields">
          <h1>Sign In</h1>
          {error && <div className="auth-form__error">{error}</div>}
          <input
            type="text"
            placeholder="Username or email"
            required
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
          />
          <input
            type="password"
            placeholder="Password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <button type="submit" disabled={submitting}>
            {submitting ? "Signing in…" : "Sign In"}
          </button>
          <div className="auth-form__switch">
            No account? <Link to="/signup">Sign Up</Link>
          </div>
        </form>
      </GlassCard>
    </div>
  );
}
