import { useNavigate } from "react-router-dom";
import type { Notification } from "../types";
import styles from "./NotificationItem.module.css";

interface Props {
  notification: Notification;
  isRead: boolean;
}

function formatTime(isoString?: string): string {
  if (!isoString) return "";
  const diff = Date.now() - new Date(isoString).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 60) return `${minutes}分前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}時間前`;
  return `${Math.floor(hours / 24)}日前`;
}

export default function NotificationItem({ notification, isRead }: Props) {
  const navigate = useNavigate();
  const isLike = notification.type === "LIKE";

  return (
    <div
      className={`${styles.item} ${isRead ? styles.read : styles.unread}`}
      onClick={() => navigate(`/posts/${notification.postId}`)}
    >
      <span className={styles.icon}>{isLike ? "♥" : "💬"}</span>
      <div className={styles.body}>
        <p className={styles.text}>
          <strong>@{notification.actorId}</strong>
          {isLike ? " があなたの投稿にいいねしました" : " があなたの投稿に返信しました"}
        </p>
        <span className={styles.time}>{formatTime(notification.receivedAt)}</span>
      </div>
    </div>
  );
}
