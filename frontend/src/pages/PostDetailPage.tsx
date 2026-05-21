import { useEffect, useState, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { createPost, listReplies, likePost, unlikePost, deletePost } from "../api/client";
// Since we don't have a direct getPost API, we'll try to find it from timeline or rely on listReplies parent logic.
// Actually, backend has no GET /posts/:id API for single post. Wait, api-spec says: 
// GET /posts/:post_id ? No, it's GET /posts/:post_id which returns "投稿の返信一覧を取得".
// So how do we display the parent post? 
// We might not have the parent post data if directly visiting the URL, but let's assume we can fetch it or just display replies for now.
// For the sake of this simple app, we can fetch the timeline and find it, or we just render replies if parent isn't found.
// Let's implement fetching timeline to search for the post as a fallback, though it might not find older posts.
import { getTimeline } from "../api/client";
import type { Post } from "../types";
import { useAuth } from "../contexts/AuthContext";
import PostCard from "../components/PostCard";
import PostForm from "../components/PostForm";
import styles from "./PostDetailPage.module.css";

export default function PostDetailPage() {
  const { postId } = useParams<{ postId: string }>();
  const { userId } = useAuth();
  const navigate = useNavigate();

  const [parentPost, setParentPost] = useState<Post | null>(null);
  const [replies, setReplies] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchReplies = useCallback(async () => {
    if (!postId) return;
    try {
      const data = await listReplies(postId);
      setReplies(data);
    } catch (err) {
      console.error(err);
    }
  }, [postId]);

  useEffect(() => {
    if (!postId) return;
    setLoading(true);

    // parentPost is ideally passed via state, but if not, we try timeline
    getTimeline(100, 0)
      .then((posts) => {
        const found = posts.find((p) => p.id === postId);
        if (found) setParentPost(found);
      })
      .catch(console.error)
      .finally(() => {
        fetchReplies().finally(() => setLoading(false));
      });
  }, [postId, fetchReplies]);

  const handlePosted = () => {
    fetchReplies();
  };

  const handleLikeToggle = async (id: string, isLiked: boolean) => {
    try {
      if (isLiked) {
        await unlikePost(id);
        if (parentPost?.id === id) {
          setParentPost({ ...parentPost, isLiked: false, likesCount: Math.max(0, parentPost.likesCount - 1) });
        }
        setReplies((prev) =>
          prev.map((p) => (p.id === id ? { ...p, isLiked: false, likesCount: Math.max(0, p.likesCount - 1) } : p))
        );
      } else {
        await likePost(id);
        if (parentPost?.id === id) {
          setParentPost({ ...parentPost, isLiked: true, likesCount: parentPost.likesCount + 1 });
        }
        setReplies((prev) =>
          prev.map((p) => (p.id === id ? { ...p, isLiked: true, likesCount: p.likesCount + 1 } : p))
        );
      }
    } catch (err) {
      console.error(err);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deletePost(id);
      if (parentPost?.id === id) {
        navigate(-1);
      } else {
        setReplies((prev) => prev.filter((p) => p.id !== id));
      }
    } catch (err) {
      console.error(err);
      alert("削除に失敗しました");
    }
  };

  if (loading) return <div className={styles.loading}>読み込み中...</div>;

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <button className={styles.backBtn} onClick={() => navigate(-1)}>
          ←
        </button>
        <h2 className={styles.headerTitle}>投稿</h2>
      </div>

      {parentPost ? (
        <PostCard
          post={parentPost}
          currentUserId={userId}
          onLikeToggle={handleLikeToggle}
          onDelete={handleDelete}
          clickable={false}
        />
      ) : (
        <div className={styles.error}>親投稿の情報を取得できませんでした。</div>
      )}

      <PostForm
        parentId={postId}
        onPosted={handlePosted}
        placeholder="返信を書く..."
        onSubmit={createPost}
      />

      <div className={styles.replies}>
        {replies.map((reply) => (
          <PostCard
            key={reply.id}
            post={reply}
            currentUserId={userId}
            onLikeToggle={handleLikeToggle}
            onDelete={handleDelete}
          />
        ))}
      </div>
    </div>
  );
}
