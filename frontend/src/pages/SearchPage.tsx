import { useState, useEffect } from "react";
import { searchUsers, listFollows, follow, unfollow } from "../api/client";
import type { User, Follow } from "../types";
import { useAuth } from "../contexts/AuthContext";
import UserCard from "../components/UserCard";
import styles from "./SearchPage.module.css";

export default function SearchPage() {
  const { username: myUsername } = useAuth();
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [users, setUsers] = useState<User[]>([]);
  const [follows, setFollows] = useState<Follow[]>([]);
  const [loading, setLoading] = useState(false);

  // デバウンス処理
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(query);
    }, 300);
    return () => clearTimeout(timer);
  }, [query]);

  // 自分のフォロー一覧を取得しておく
  useEffect(() => {
    if (myUsername) {
      listFollows(myUsername)
        .then(setFollows)
        .catch(console.error);
    }
  }, [myUsername]);

  // 検索実行
  useEffect(() => {
    if (!debouncedQuery.trim()) {
      setUsers([]);
      return;
    }
    setLoading(true);
    searchUsers(debouncedQuery)
      .then(setUsers)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [debouncedQuery]);

  const handleFollowToggle = async (targetUsername: string, isFollowing: boolean) => {
    try {
      if (isFollowing) {
        await unfollow(targetUsername);
        setFollows((prev) => prev.filter((f) => f.username !== targetUsername));
      } else {
        await follow(targetUsername);
        setFollows((prev) => [...prev, { id: "", username: targetUsername }]);
      }
    } catch (err) {
      console.error(err);
      alert("フォロー操作に失敗しました");
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <div className={styles.searchBox}>
          <span className={styles.searchIcon}>🔍</span>
          <input
            type="text"
            className={styles.searchInput}
            placeholder="ユーザー名を検索..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            autoFocus
          />
        </div>
      </div>

      <div className={styles.results}>
        {loading && <div className={styles.message}>検索中...</div>}
        
        {!loading && query && users.length === 0 && (
          <div className={styles.message}>
            <p>見つかりませんでした</p>
            <p className={styles.note}>※ 現在、バックエンドの検索APIが未実装のため結果は表示されません。</p>
          </div>
        )}

        {!loading && users.map((u) => {
          const isFollowing = follows.some((f) => f.username === u.username);
          const isMe = u.username === myUsername;
          return (
            <UserCard
              key={u.id}
              user={u}
              isFollowing={isFollowing}
              isMe={isMe}
              onFollowToggle={handleFollowToggle}
            />
          );
        })}
      </div>
    </div>
  );
}
