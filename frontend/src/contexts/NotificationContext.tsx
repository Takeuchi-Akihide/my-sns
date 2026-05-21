import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import type { ReactNode } from "react";
import type { Notification } from "../types";
import { useAuth } from "./AuthContext";

interface NotificationContextType {
  unreadCount: number;
  notifications: Notification[];
  addNotification: (n: Notification) => void;
  markAllAsRead: () => void;
  clearNotifications: () => void;
}

const NotificationContext = createContext<NotificationContextType | null>(null);

const WS_URL = "ws://localhost:3030/api/v1/ws";

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { token } = useAuth();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const wsRef = useRef<WebSocket | null>(null);

  const addNotification = useCallback((n: Notification) => {
    setNotifications((prev) => [n, ...prev]);
    setUnreadCount((c) => c + 1);
  }, []);

  const markAllAsRead = useCallback(() => {
    setUnreadCount(0);
  }, []);

  const clearNotifications = useCallback(() => {
    setNotifications([]);
    setUnreadCount(0);
  }, []);

  // WebSocket 接続管理
  useEffect(() => {
    if (!token) {
      wsRef.current?.close();
      wsRef.current = null;
      return;
    }

    const ws = new WebSocket(`${WS_URL}?token=${token}`);
    wsRef.current = ws;

    ws.onmessage = (event) => {
      try {
        const raw = JSON.parse(event.data as string);
        const notification: Notification = {
          recipientId: raw["recipient-id"],
          actorId: raw["actor-id"],
          postId: raw["post-id"],
          type: raw["type"],
          receivedAt: new Date().toISOString(),
        };
        addNotification(notification);
      } catch {
        console.error("Failed to parse WS message:", event.data);
      }
    };

    ws.onerror = (e) => {
      console.error("WebSocket error:", e);
    };

    return () => {
      ws.close();
    };
  }, [token, addNotification]);

  return (
    <NotificationContext.Provider
      value={{ unreadCount, notifications, addNotification, markAllAsRead, clearNotifications }}
    >
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotification(): NotificationContextType {
  const ctx = useContext(NotificationContext);
  if (!ctx) throw new Error("useNotification must be used within NotificationProvider");
  return ctx;
}
