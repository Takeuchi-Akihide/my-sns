import { useState } from "react";
import styles from "./PostForm.module.css";

interface Props {
  parentId?: string;
  onPosted: () => void;
  placeholder?: string;
  onSubmit: (content: string, parentId?: string) => Promise<void>;
}

const MAX_LENGTH = 280;

export default function PostForm({
  parentId,
  onPosted,
  placeholder = "今なにしてる？",
  onSubmit,
}: Props) {
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const remaining = MAX_LENGTH - content.length;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!content.trim()) return;
    setLoading(true);
    setError("");
    try {
      await onSubmit(content.trim(), parentId);
      setContent("");
      onPosted();
    } catch (err) {
      setError(err instanceof Error ? err.message : "投稿に失敗しました");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit}>
      <textarea
        className={styles.textarea}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder={placeholder}
        maxLength={MAX_LENGTH}
        rows={3}
      />
      <div className={styles.footer}>
        <span className={`${styles.counter} ${remaining < 20 ? styles.warn : ""}`}>
          {remaining}
        </span>
        {error && <span className={styles.error}>{error}</span>}
        <button
          type="submit"
          className={styles.submitBtn}
          disabled={!content.trim() || loading}
        >
          {loading ? "送信中…" : parentId ? "返信する" : "投稿する"}
        </button>
      </div>
    </form>
  );
}
