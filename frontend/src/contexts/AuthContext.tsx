import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { getCurrentUser } from "../api/client";

interface AuthContextType {
  token: string | null;
  username: string | null;
  userId: string | null;
  login: (token: string, username?: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

function decodeJwtPayload(token: string): { user_id?: string; username?: string } {
  try {
    const payload = token.split(".")[1];
    return JSON.parse(atob(payload));
  } catch {
    return {};
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() =>
    localStorage.getItem("token")
  );
  const [username, setUsername] = useState<string | null>(() => {
    const t = localStorage.getItem("token");
    if (!t) return null;
    return localStorage.getItem("username") ?? decodeJwtPayload(t).username ?? null;
  });
  const [userId, setUserId] = useState<string | null>(() => {
    const t = localStorage.getItem("token");
    if (!t) return null;
    return decodeJwtPayload(t).user_id ?? null;
  });

  function login(newToken: string, fallbackUsername?: string) {
    const payload = decodeJwtPayload(newToken);
    const nextUsername = payload.username ?? fallbackUsername ?? null;
    localStorage.setItem("token", newToken);
    if (nextUsername) {
      localStorage.setItem("username", nextUsername);
    }
    setToken(newToken);
    setUserId(payload.user_id ?? null);
    setUsername(nextUsername);
  }

  function logout() {
    localStorage.removeItem("token");
    localStorage.removeItem("username");
    setToken(null);
    setUserId(null);
    setUsername(null);
  }

  // トークン有効期限チェック
  useEffect(() => {
    if (!token) return;
    const payload = decodeJwtPayload(token);
    if (payload.user_id) {
      setUserId(payload.user_id);
    }
    if (payload.username) {
      localStorage.setItem("username", payload.username);
      setUsername(payload.username);
      return;
    }
    if (!username) {
      getCurrentUser()
        .then((user) => {
          localStorage.setItem("username", user.username);
          setUsername(user.username);
          setUserId(user.id);
        })
        .catch(() => undefined);
    }
  }, [token, username]);

  return (
    <AuthContext.Provider value={{ token, username, userId, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
