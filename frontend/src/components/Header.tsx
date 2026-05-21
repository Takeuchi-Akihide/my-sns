import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import { useNotification } from "../contexts/NotificationContext";
import styles from "./Header.module.css";

export default function Header() {
  const { username, logout } = useAuth();
  const { unreadCount } = useNotification();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link to="/" className={styles.logo}>
          my-sns
        </Link>
        <nav className={styles.nav}>
          <Link to="/search" className={styles.navBtn} title="検索">
            🔍
          </Link>
          <Link to="/notifications" className={styles.navBtn} title="通知">
            🔔
            {unreadCount > 0 && (
              <span className={styles.badge}>
                {unreadCount > 99 ? "99+" : unreadCount}
              </span>
            )}
          </Link>
          {username && (
            <Link
              to={`/users/${username}`}
              className={styles.navBtn}
              title="プロフィール"
            >
              @{username}
            </Link>
          )}
          <button
            className={styles.navBtn}
            onClick={() => navigate("/settings")}
            title="設定"
          >
            ⚙️
          </button>
          <button
            className={styles.navBtn}
            onClick={handleLogout}
            title="ログアウト"
          >
            🚪
          </button>
        </nav>
      </div>
    </header>
  );
}
