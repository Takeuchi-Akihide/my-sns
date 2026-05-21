import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { login as apiLogin } from "../api/client";
import { ApiError } from "../api/client";
import { useAuth } from "../contexts/AuthContext";
import styles from "./AuthPage.module.css";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await apiLogin(username, password);
      login(res.token, res.username ?? username);
      navigate("/", { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setError("ユーザー名またはパスワードが正しくありません");
      } else {
        setError("ログインに失敗しました。しばらく後に再試行してください。");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <h1 className={styles.logo}>my-sns</h1>
        <h2 className={styles.title}>ログイン</h2>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.field}>
            <label htmlFor="username">ユーザー名</label>
            <input
              id="username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              autoComplete="username"
              autoFocus
            />
          </div>
          <div className={styles.field}>
            <label htmlFor="password">パスワード</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
            />
          </div>

          {error && <p className={styles.error}>{error}</p>}

          <button
            type="submit"
            className={styles.submitBtn}
            disabled={loading || !username || !password}
          >
            {loading ? "ログイン中…" : "ログイン"}
          </button>
        </form>

        <p className={styles.link}>
          アカウントをお持ちでない方は{" "}
          <Link to="/register">新規登録</Link>
        </p>
      </div>
    </div>
  );
}
