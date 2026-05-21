import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { Notification } from "../types";
import styles from "./NotificationToast.module.css";

interface Props {
  notification: Notification;
}

function buildMessage(n: Notification): string {
  if (n.type === "LIKE") return `@${n.actorId} があなたの投稿にいいねしました`;
  if (n.type === "REPLY") return `@${n.actorId} があなたの投稿に返信しました`;
  return "新しい通知があります";
}

export function Toast({ notification }: Props) {
  const [visible, setVisible] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    const timer = setTimeout(() => setVisible(false), 3500);
    return () => clearTimeout(timer);
  }, []);

  if (!visible) return null;

  return (
    <div
      className={styles.toast}
      onClick={() => navigate(`/posts/${notification.postId}`)}
    >
      <span className={styles.icon}>
        {notification.type === "LIKE" ? "♥" : "💬"}
      </span>
      <span className={styles.message}>{buildMessage(notification)}</span>
    </div>
  );
}

import { useNotification } from "../contexts/NotificationContext";

export default function NotificationToast() {
  const { notifications } = useNotification();
  const latest = notifications[0];

  if (!latest) return null;

  return (
    <div className={styles.container}>
      <Toast key={latest.receivedAt ?? latest.postId} notification={latest} />
    </div>
  );
}
