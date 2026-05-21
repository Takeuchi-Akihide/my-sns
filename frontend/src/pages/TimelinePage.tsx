import { useEffect, useState, useCallback } from "react";
import { getTimeline, createPost, likePost, unlikePost, deletePost, getCurrentUser } from "../api/client";
import type { Post, User } from "../types";
import { useAuth } from "../contexts/AuthContext";
import PostCard from "../components/PostCard";
import PostForm from "../components/PostForm";
import styles from "./TimelinePage.module.css";

export default function TimelinePage() {
  const { userId } = useAuth();
  const [posts, setPosts] = useState<Post[]>([]);
  const [offset, setOffset] = useState(0);
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const LIMIT = 20;

  const loadPosts = useCallback(async (currentOffset: number, append = false) => {
    if (loading || (!hasMore && append)) return;
    setLoading(true);
    try {
      const newPosts = await getTimeline(LIMIT, currentOffset);
      if (newPosts.length < LIMIT) {
        setHasMore(false);
      }
      setPosts((prev) => (append ? [...prev, ...newPosts] : newPosts));
      setOffset(currentOffset + newPosts.length);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [loading, hasMore]);

  useEffect(() => {
    getCurrentUser()
      .then(setCurrentUser)
      .catch((err) => console.error(err));
    loadPosts(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handlePosted = () => {
    setHasMore(true);
    loadPosts(0);
  };

  const handleLikeToggle = async (postId: string, isLiked: boolean) => {
    try {
      if (isLiked) {
        await unlikePost(postId);
        setPosts((prev) =>
          prev.map((p) =>
            p.id === postId ? { ...p, isLiked: false, likesCount: Math.max(0, p.likesCount - 1) } : p
          )
        );
      } else {
        await likePost(postId);
        setPosts((prev) =>
          prev.map((p) =>
            p.id === postId ? { ...p, isLiked: true, likesCount: p.likesCount + 1 } : p
          )
        );
      }
    } catch (err) {
      console.error(err);
    }
  };

  const handleDelete = async (postId: string) => {
    try {
      await deletePost(postId);
      setPosts((prev) => prev.filter((p) => p.id !== postId));
    } catch (err) {
      console.error(err);
      alert("削除に失敗しました");
    }
  };

  return (
    <div className={styles.container}>
      <h2 className={styles.header}>ホーム</h2>
      {currentUser && (
        <div className={styles.currentUser}>
          <div>
            <div className={styles.displayName}>{currentUser.displayName}</div>
            <div className={styles.username}>@{currentUser.username}</div>
          </div>
        </div>
      )}
      <PostForm onPosted={handlePosted} onSubmit={createPost} />
      <div className={styles.feed}>
        {posts.map((post) => (
          <PostCard
            key={post.id}
            post={post}
            currentUserId={userId}
            onLikeToggle={handleLikeToggle}
            onDelete={handleDelete}
          />
        ))}
      </div>
      {hasMore && (
        <button
          className={styles.loadMoreBtn}
          onClick={() => loadPosts(offset, true)}
          disabled={loading}
        >
          {loading ? "読み込み中..." : "もっと読み込む"}
        </button>
      )}
    </div>
  );
}
