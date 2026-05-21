import { Link, useNavigate } from "react-router-dom";
import type { Post } from "../types";
import styles from "./PostCard.module.css";

interface Props {
  post: Post;
  currentUserId: string | null;
  onLikeToggle: (postId: string, isLiked: boolean) => void;
  onDelete?: (postId: string) => void;
  /** クリック時に詳細へ遷移するか（詳細ページ内ではfalseに） */
  clickable?: boolean;
}

function formatRelativeTime(isoString: string): string {
  const diff = Date.now() - new Date(isoString).getTime();
  const seconds = Math.floor(diff / 1000);
  if (seconds < 60) return `${seconds}秒前`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}分前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}時間前`;
  const days = Math.floor(hours / 24);
  return `${days}日前`;
}

export default function PostCard({
  post,
  currentUserId,
  onLikeToggle,
  onDelete,
  clickable = true,
}: Props) {
  const navigate = useNavigate();
  const isOwner = currentUserId === post.userId;

  function handleCardClick() {
    if (clickable) navigate(`/posts/${post.id}`);
  }

  function handleLike(e: React.MouseEvent) {
    e.stopPropagation();
    onLikeToggle(post.id, post.isLiked ?? false);
  }

  function handleDelete(e: React.MouseEvent) {
    e.stopPropagation();
    if (confirm("この投稿を削除しますか？")) {
      onDelete?.(post.id);
    }
  }

  return (
    <article
      className={`${styles.card} ${clickable ? styles.clickable : ""}`}
      onClick={handleCardClick}
    >
      <div className={styles.meta}>
        <Link
          to={`/users/${post.username}`}
          className={styles.username}
          onClick={(e) => e.stopPropagation()}
        >
          @{post.username}
        </Link>
        {post.displayName && (
          <span className={styles.displayName}>{post.displayName}</span>
        )}
        <span className={styles.time}>{formatRelativeTime(post.createdAt)}</span>
      </div>

      <p className={styles.content}>{post.content}</p>

      <div className={styles.actions}>
        <button
          className={`${styles.actionBtn} ${post.isLiked ? styles.liked : ""}`}
          onClick={handleLike}
          title={post.isLiked ? "いいね解除" : "いいね"}
        >
          {post.isLiked ? "♥" : "♡"} {post.likesCount}
        </button>
        <span className={styles.actionBtn}>
          💬 {post.repliesCount}
        </span>
        {isOwner && onDelete && (
          <button
            className={`${styles.actionBtn} ${styles.deleteBtn}`}
            onClick={handleDelete}
            title="削除"
          >
            🗑
          </button>
        )}
      </div>
    </article>
  );
}
