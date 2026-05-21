import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  getUser,
  listPosts,
  listLikedPosts,
  listFollows,
  listFollowers,
  follow,
  unfollow,
  likePost,
  unlikePost,
  deletePost,
} from "../api/client";
import type { User, Post, Follow } from "../types";
import { useAuth } from "../contexts/AuthContext";
import PostCard from "../components/PostCard";
import styles from "./ProfilePage.module.css";

type TabType = "posts" | "likes" | "follows" | "followers";

export default function ProfilePage() {
  const { username } = useParams<{ username: string }>();
  const { userId, username: myUsername } = useAuth();
  const navigate = useNavigate();

  const [user, setUser] = useState<User | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const [likedPosts, setLikedPosts] = useState<Post[]>([]);
  const [follows, setFollows] = useState<Follow[]>([]);
  const [followers, setFollowers] = useState<Follow[]>([]);
  
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [activeTab, setActiveTab] = useState<TabType>("posts");

  const [isFollowing, setIsFollowing] = useState(false);

  useEffect(() => {
    setActiveTab("posts");
  }, [username]);

  useEffect(() => {
    if (!username) return;
    setLoading(true);
    setError("");
    Promise.all([
      getUser(username).catch((err) => {
        throw new Error(err.details || "ユーザーの取得に失敗しました");
      }),
      listPosts(username, 100),
      listLikedPosts(username, 100),
      listFollows(username, 100),
      listFollowers(username, 100),
    ])
      .then(async ([userData, postsData, likedData, followsData, followersData]) => {
        setUser(userData);
        setPosts(postsData);
        setLikedPosts(likedData);
        setFollows(followsData);
        setFollowers(followersData);
        if (!myUsername || myUsername === username) {
          setIsFollowing(false);
          return;
        }

        const followedByMe = followersData.some((f) => f.username === myUsername || f.id === userId);
        if (followedByMe) {
          setIsFollowing(true);
          return;
        }

        const myFollows = await listFollows(myUsername, 100);
        setIsFollowing(myFollows.some((f) => f.id === userData.id || f.username === userData.username));
      })
      .catch((err) => {
        setError(err.message);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [username, myUsername, userId]);

  const handleFollowToggle = async () => {
    if (!username) return;
    try {
      if (isFollowing) {
        await unfollow(username);
        setIsFollowing(false);
        setFollowers((prev) => prev.filter((f) => f.username !== myUsername && f.id !== userId));
      } else {
        await follow(username);
        setIsFollowing(true);
        if (myUsername) {
          setFollowers((prev) =>
            prev.some((f) => f.username === myUsername)
              ? prev
              : [{ id: userId ?? "", username: myUsername }, ...prev]
          );
        }
      }
    } catch (err) {
      console.error(err);
      alert("フォロー操作に失敗しました");
    }
  };

  const handleLikeToggle = async (postId: string, isLiked: boolean) => {
    const updateLikedState = (targetIsLiked: boolean, delta: number) => (post: Post) =>
      post.id === postId
        ? { ...post, isLiked: targetIsLiked, likesCount: Math.max(0, post.likesCount + delta) }
        : post;

    try {
      if (isLiked) {
        await unlikePost(postId);
        setPosts((prev) => prev.map(updateLikedState(false, -1)));
        setLikedPosts((prev) => prev.map(updateLikedState(false, -1)));
      } else {
        await likePost(postId);
        setPosts((prev) => prev.map(updateLikedState(true, 1)));
        setLikedPosts((prev) => prev.map(updateLikedState(true, 1)));
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

  if (loading) return <div className={styles.loading}>読み込み中...</div>;
  if (error || !user) return <div className={styles.error}>{error || "ユーザーが見つかりません"}</div>;

  const isMe = user.id === userId || user.username === myUsername;

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <button className={styles.backBtn} onClick={() => navigate(-1)}>
          ←
        </button>
        <div className={styles.headerInfo}>
          <h2 className={styles.headerName}>{user.displayName}</h2>
          <span className={styles.postCount}>{posts.length} 件の投稿</span>
        </div>
      </div>

      <div className={styles.profile}>
        <div className={styles.profileTop}>
          <div className={styles.avatarPlaceholder} />
          {isMe ? (
            <span className={styles.selfBadge}>自分のプロフィール</span>
          ) : (
            <button
              className={`${styles.actionBtn} ${isFollowing ? styles.following : ""}`}
              onClick={handleFollowToggle}
            >
              {isFollowing ? "フォロー中" : "フォローする"}
            </button>
          )}
        </div>
        <div className={styles.userInfo}>
          <h1 className={styles.displayName}>{user.displayName}</h1>
          <p className={styles.username}>@{user.username}</p>
          {user.bio && <p className={styles.bio}>{user.bio}</p>}
          <div className={styles.joinedAt}>
            📅 {new Date(user.createdAt).toLocaleDateString()} から利用しています
          </div>
        </div>
      </div>

      <div className={styles.tabs}>
        <button
          className={`${styles.tabBtn} ${activeTab === "posts" ? styles.activeTab : ""}`}
          onClick={() => setActiveTab("posts")}
        >
          投稿
        </button>
        <button
          className={`${styles.tabBtn} ${activeTab === "likes" ? styles.activeTab : ""}`}
          onClick={() => setActiveTab("likes")}
        >
          いいね
        </button>
        <button
          className={`${styles.tabBtn} ${activeTab === "follows" ? styles.activeTab : ""}`}
          onClick={() => setActiveTab("follows")}
        >
          フォロー
        </button>
        <button
          className={`${styles.tabBtn} ${activeTab === "followers" ? styles.activeTab : ""}`}
          onClick={() => setActiveTab("followers")}
        >
          フォロワー
        </button>
      </div>

      <div className={styles.feed}>
        {activeTab === "posts" && posts.map((post) => (
          <PostCard
            key={post.id}
            post={post}
            currentUserId={userId}
            onLikeToggle={handleLikeToggle}
            onDelete={handleDelete}
          />
        ))}

        {activeTab === "likes" && (
          likedPosts.length > 0 ? likedPosts.map((post) => (
            <PostCard
              key={post.id}
              post={post}
              currentUserId={userId}
              onLikeToggle={handleLikeToggle}
              onDelete={handleDelete}
            />
          )) : <div className={styles.emptyMsg}>まだいいねした投稿はありません</div>
        )}

        {activeTab === "follows" && (
          follows.length > 0 ? follows.map((f) => (
            <div key={f.id || f.username} className={styles.followItem} onClick={() => navigate(`/users/${f.username}`)}>
              <span className={styles.followIcon}>👤</span>
              <span className={styles.followName}>@{f.username}</span>
            </div>
          )) : <div className={styles.emptyMsg}>フォローしているユーザーはいません</div>
        )}

        {activeTab === "followers" && (
          followers.length > 0 ? followers.map((f) => (
            <div key={f.id || f.username} className={styles.followItem} onClick={() => navigate(`/users/${f.username}`)}>
              <span className={styles.followIcon}>👤</span>
              <span className={styles.followName}>@{f.username}</span>
            </div>
          )) : <div className={styles.emptyMsg}>フォロワーはいません</div>
        )}
      </div>
    </div>
  );
}
