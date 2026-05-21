import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { registerUser, login as apiLogin } from "../api/client";
import { ApiError } from "../api/client";
import { useAuth } from "../contexts/AuthContext";
import styles from "./AuthPage.module.css";

export default function RegisterPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({
    username: "",
    displayName: "",
    email: "",
    password: "",
  });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await registerUser(form.username, form.displayName, form.email, form.password);
      const res = await apiLogin(form.username, form.password);
      login(res.token, res.username ?? form.username);
      navigate("/", { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.details);
      } else {
        setError("登録に失敗しました。しばらく後に再試行してください。");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <h1 className={styles.logo}>my-sns</h1>
        <h2 className={styles.title}>新規登録</h2>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.field}>
            <label htmlFor="username">ユーザー名</label>
            <input
              id="username"
              name="username"
              type="text"
              value={form.username}
              onChange={handleChange}
              required
              autoFocus
            />
          </div>
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
          <div className={styles.field}>
            <label htmlFor="password">パスワード</label>
            <input
              id="password"
              name="password"
              type="password"
              value={form.password}
              onChange={handleChange}
              required
            />
          </div>

          {error && <p className={styles.error}>{error}</p>}

          <button
            type="submit"
            className={styles.submitBtn}
            disabled={loading || Object.values(form).some((v) => !v)}
          >
            {loading ? "登録中…" : "登録する"}
          </button>
        </form>

        <p className={styles.link}>
          すでにアカウントをお持ちの方は <Link to="/login">ログイン</Link>
        </p>
      </div>
    </div>
  );
}
