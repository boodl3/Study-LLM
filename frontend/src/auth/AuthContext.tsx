import {
  createContext,
  useContext,
  useState,
  useCallback,
  type ReactNode,
} from "react";
import * as authApi from "../api/auth";
import { getToken, setToken } from "../api/client";

interface AuthState {
  userId: string | null;
  username: string | null;
  email: string | null;
}

interface AuthContextValue extends AuthState {
  isAuthenticated: boolean;
  login: (identifier: string, password: string) => Promise<void>;
  signup: (
    username: string,
    email: string,
    password: string,
    confirmPassword: string,
  ) => Promise<void>;
  logout: () => void;
  updateProfile: (username: string, email: string) => Promise<void>;
  changePassword: (
    currentPassword: string,
    newPassword: string,
    confirmNewPassword: string,
  ) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const STORED_USERNAME_KEY = "studyllm_username";
const STORED_EMAIL_KEY = "studyllm_email";
const STORED_USER_ID_KEY = "studyllm_user_id";

// Holds auth state in memory, backed by localStorage so a page refresh doesn't log the user out
// (the token itself lives separately, see api/client.ts).
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    userId: getToken() ? localStorage.getItem(STORED_USER_ID_KEY) : null,
    username: getToken() ? localStorage.getItem(STORED_USERNAME_KEY) : null,
    email: getToken() ? localStorage.getItem(STORED_EMAIL_KEY) : null,
  });

  // Persists a fresh login/signup response (token + profile) and syncs it into state.
  const applyAuth = useCallback((res: authApi.AuthResponse) => {
    setToken(res.token);
    localStorage.setItem(STORED_USER_ID_KEY, res.userId);
    localStorage.setItem(STORED_USERNAME_KEY, res.username);
    localStorage.setItem(STORED_EMAIL_KEY, res.email);
    setState({ userId: res.userId, username: res.username, email: res.email });
  }, []);

  const login = useCallback(
    async (identifier: string, password: string) => {
      applyAuth(await authApi.login(identifier, password));
    },
    [applyAuth],
  );

  const signup = useCallback(
    async (username: string, email: string, password: string, confirmPassword: string) => {
      applyAuth(await authApi.signup(username, email, password, confirmPassword));
    },
    [applyAuth],
  );

  const logout = useCallback(() => {
    setToken(null);
    localStorage.removeItem(STORED_USER_ID_KEY);
    localStorage.removeItem(STORED_USERNAME_KEY);
    localStorage.removeItem(STORED_EMAIL_KEY);
    setState({ userId: null, username: null, email: null });
  }, []);

  const updateProfile = useCallback(async (username: string, email: string) => {
    const profile = await authApi.updateProfile(username, email);
    localStorage.setItem(STORED_USERNAME_KEY, profile.username);
    localStorage.setItem(STORED_EMAIL_KEY, profile.email);
    setState((prev) => ({ ...prev, username: profile.username, email: profile.email }));
  }, []);

  const changePassword = useCallback(
    async (currentPassword: string, newPassword: string, confirmNewPassword: string) => {
      await authApi.changePassword(currentPassword, newPassword, confirmNewPassword);
    },
    [],
  );

  return (
    <AuthContext.Provider
      value={{
        ...state,
        isAuthenticated: state.userId !== null,
        login,
        signup,
        logout,
        updateProfile,
        changePassword,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}
