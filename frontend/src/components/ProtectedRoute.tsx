import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import Header from "./Header";
import NotificationToast from "./NotificationToast";
import styles from "./Layout.module.css";

export default function ProtectedRoute() {
  const { token } = useAuth();
  if (!token) return <Navigate to="/login" replace />;

  return (
    <div className={styles.layout}>
      <Header />
      <main className={styles.main}>
        <Outlet />
      </main>
      <NotificationToast />
    </div>
  );
}
