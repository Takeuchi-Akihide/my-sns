import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useNotification } from "../contexts/NotificationContext";
import NotificationItem from "../components/NotificationItem";
import styles from "./NotificationsPage.module.css";

export default function NotificationsPage() {
  const { notifications, markAllAsRead } = useNotification();
  const navigate = useNavigate();

  useEffect(() => {
    // 画面を開いた時点で未読バッジを消す
    markAllAsRead();
  }, [markAllAsRead]);

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <button className={styles.backBtn} onClick={() => navigate(-1)}>
          ←
        </button>
        <h2 className={styles.headerTitle}>通知</h2>
      </div>

      <div className={styles.list}>
        {notifications.length === 0 ? (
          <div className={styles.empty}>
            <p>新しい通知はありません</p>
            <p className={styles.note}>
              ※ 現在のバージョンでは、ログイン後に受信した通知のみ表示されます。
            </p>
          </div>
        ) : (
          notifications.map((n, i) => (
            <NotificationItem
              key={n.receivedAt || i}
              notification={n}
              isRead={false} // 今回は簡易的にすべて未読スタイル（または既読スタイル）で扱う
            />
          ))
        )}
      </div>
    </div>
  );
}
