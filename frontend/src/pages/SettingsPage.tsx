import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getCurrentUser, updateUser, deleteUser } from "../api/client";
import { useAuth } from "../contexts/AuthContext";
import type { User } from "../types";
import styles from "./SettingsPage.module.css";

export default function SettingsPage() {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({
    displayName: "",
    bio: "",
    email: "",
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [currentUser, setCurrentUser] = useState<User | null>(null);

  useEffect(() => {
    setLoading(true);
    getCurrentUser()
      .then((user) => {
        setCurrentUser(user);
        setForm({
          displayName: user.displayName || "",
          bio: user.bio || "",
          email: user.email || "",
        });
      })
      .catch((err) => {
        setError(err.message);
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      await updateUser({
        display_name: form.displayName,
        bio: form.bio,
        email: form.email,
      });
      setSuccess("保存しました");
    } catch (err) {
      if (err instanceof Error) setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const handleDeleteAccount = async () => {
    if (confirm("本当にアカウントを削除しますか？この操作は取り消せません。")) {
      try {
        await deleteUser();
        logout();
        navigate("/login");
      } catch (err) {
        if (err instanceof Error) alert(err.message);
      }
    }
  };

  if (loading) return <div className={styles.loading}>読み込み中...</div>;

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <button className={styles.backBtn} onClick={() => navigate(-1)}>
          ←
        </button>
        <h2 className={styles.headerTitle}>設定</h2>
      </div>

      <div className={styles.content}>
        <form className={styles.form} onSubmit={handleSubmit}>
          {currentUser && (
            <div className={styles.profileLinkPanel}>
              <div>
                <div className={styles.profileLinkTitle}>@{currentUser.username}</div>
                <div className={styles.profileLinkText}>投稿、いいね、フォロー、フォロワーを確認できます。</div>
              </div>
              <button
                type="button"
                className={styles.profileLinkBtn}
                onClick={() => navigate(`/users/${currentUser.username}`)}
              >
                プロフィールを表示
              </button>
            </div>
          )}

          <div className={styles.field}>
            <label htmlFor="displayName">表示名</label>
            <input
              id="displayName"
              name="displayName"
              type="text"
              value={form.displayName}
              onChange={handleChange}
              required
            />
          </div>
          <div className={styles.field}>
            <label htmlFor="bio">自己紹介</label>
            <textarea
              id="bio"
              name="bio"
              value={form.bio}
              onChange={handleChange}
              rows={4}
            />
          </div>
          <div className={styles.field}>
            <label htmlFor="email">メールアドレス</label>
            <input
              id="email"
              name="email"
              type="email"
              value={form.email}
              onChange={handleChange}
              required
            />
          </div>

          {error && <div className={styles.error}>{error}</div>}
          {success && <div className={styles.success}>{success}</div>}

          <button type="submit" className={styles.saveBtn} disabled={saving}>
            {saving ? "保存中..." : "保存する"}
          </button>
        </form>

        <hr className={styles.divider} />

        <div className={styles.dangerZone}>
          <button className={styles.logoutBtn} onClick={handleLogout}>
            ログアウト
          </button>
          <button className={styles.deleteBtn} onClick={handleDeleteAccount}>
            アカウントを削除
          </button>
        </div>
      </div>
    </div>
  );
}
