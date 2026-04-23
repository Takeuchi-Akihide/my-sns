(ns my-sns.schema
  (:require [next.jdbc :as jdbc]
            [my-sns.db :refer [datasource]]))

(def ddl-statements
  [;; UUID生成関数のための拡張機能を有効化
   "CREATE EXTENSION IF NOT EXISTS \"pgcrypto\";"

   "CREATE TABLE IF NOT EXISTS users (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      username VARCHAR(50) UNIQUE NOT NULL,
      display_name VARCHAR(100) NOT NULL,
      email VARCHAR(255) UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      bio TEXT,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );"

   "CREATE TABLE IF NOT EXISTS posts (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      parent_id UUID REFERENCES posts(id) ON DELETE CASCADE,
      content TEXT NOT NULL,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );"

   "CREATE INDEX IF NOT EXISTS idx_posts_user_id_created_at ON posts (user_id, created_at DESC);"
   "CREATE INDEX IF NOT EXISTS idx_posts_parent_id ON posts(parent_id);"

   "CREATE TABLE IF NOT EXISTS follows (
      follower_id UUID REFERENCES users(id),
      followed_id UUID REFERENCES users(id),
      created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (follower_id, followed_id)
    );"

   "CREATE INDEX IF NOT EXISTS idx_follows_followed_id ON follows(followed_id);"

   "CREATE TABLE IF NOT EXISTS post_likes (
      user_id UUID REFERENCES users(id) ON DELETE CASCADE,
      post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (user_id, post_id)
    );"

   "CREATE INDEX IF NOT EXISTS idx_post_likes_post_id ON post_likes(post_id);"])

(defn add-user!
  [username display-name email password-hash]
  (jdbc/execute! datasource
                 ["INSERT INTO users (username, display_name, email, password_hash) VALUES (?, ?, ?, ?)"
                  username display-name email password-hash]))

(defn get-user-by-username
  [username]
  (jdbc/execute-one! datasource
                     ["SELECT * FROM users WHERE username = ?"
                      username]))

(defn get-user-by-id
  [user-id]
  (let [uuid (if (string? user-id)
               (java.util.UUID/fromString user-id)
               user-id)]
    (jdbc/execute-one! datasource
                       ["SELECT * FROM users WHERE id = ?"
                        uuid])))
(defn get-user-id-by-post
  [post-id]
  (jdbc/execute-one! datasource
                     ["SELECT user_id FROM posts WHERE id = ?"
                      post-id]))

(defn search-users
  [username-prefix limit]
  (jdbc/execute! datasource
                 ["SELECT id, username, bio FROM users WHERE username LIKE ? ORDER BY username LIMIT ?"
                  (str username-prefix "%") limit]))

(defn update-user!
  [user-id username display-name email password-hash bio]
  (jdbc/execute! datasource
                 ["UPDATE users SET username = ?, display_name = ?, email = ?, password_hash = ?, bio = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
                  username display-name email password-hash bio user-id]))

(defn delete-user!
  [user-id]
  (jdbc/execute! datasource
                 ["DELETE FROM users WHERE id = ?"
                  user-id]))

(defn create-post!
  [user-id parent-id content]
  (jdbc/execute-one! datasource
                     ["INSERT INTO posts (user_id, parent_id, content) VALUES (?, ?, ?) RETURNING *"
                      user-id parent-id content]))

(defn delete-post!
  [post-id]
  (jdbc/execute! datasource
                 ["DELETE FROM posts WHERE id = ?"
                  post-id]))

(defn follow-user!
  [follower-id followed-id]
  (jdbc/execute! datasource
                 ["INSERT INTO follows (follower_id, followed_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
                  follower-id followed-id]))

(defn unfollow-user!
  [follower-id followed-id]
  (jdbc/execute! datasource
                 ["DELETE FROM follows WHERE follower_id = ? AND followed_id = ?"
                  follower-id followed-id]))

(defn list-posts
  [user-id limit]
  (jdbc/execute! datasource
                 ["SELECT p.id, p.content, p.created_at, u.username
                   FROM posts p
                   JOIN users u ON p.user_id = u.id
                   WHERE p.user_id = ?
                   ORDER BY p.created_at DESC
                   LIMIT ?"
                  user-id limit]))

(defn list-follows
  [user-id]
  (jdbc/execute! datasource
                 ["SELECT u.id, u.username
                   FROM follows f
                   JOIN users u ON f.followed_id = u.id
                   WHERE f.follower_id = ?"
                  user-id]))

(defn list-timeline
  [user-id limit]
  (jdbc/execute! datasource
                 ["SELECT p.*,
                          u.username,
                          u.display_name,
                          (SELECT COUNT (*) FROM post_likes WHERE post_id = p.id) as like_count,
                          (SELECT COUNT (*) FROM posts WHERE parent_id = p.id) as reply_count,
                          EXISTS (SELECT 1 FROM post_likes WHERE post_id = p.id AND user_id = ?) as is_liked
                   FROM posts p
                   JOIN users u ON p.user_id = u.id
                   WHERE p.parent_id IS NULL
                     AND (p.user_id = ? OR p.user_id IN (SELECT followed_id FROM follows WHERE follower_id = ?))
                   ORDER BY p.created_at DESC
                   LIMIT ?;"
                  user-id user-id user-id limit]))

(defn like-post!
  [user-id post-id]
  (jdbc/execute! datasource
                 ["INSERT INTO post_likes (user_id, post_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
                  user-id post-id]))

(defn unlike-post!
  [user-id post-id]
  (jdbc/execute! datasource
                 ["DELETE FROM post_likes WHERE user_id = ? AND post_id = ?"
                  user-id post-id]))

(defn list-post-replies
  ;; 1つ下の階層の子投稿を取得する。これを使いまわすことでリプライ一覧が取得可能なはず。
  [post-id limit]
  (jdbc/execute! datasource
                 ["SELECT p.id, p.content, p.created_at, u.username
                    FROM posts p
                    JOIN users u ON p.user_id = u.id
                    WHERE p.parent_id = ?
                    ORDER BY p.created_at ASC
                    LIMIT ?"
                  post-id limit]))

(defn create-schema!
  []
  (doseq [stmt ddl-statements]
    (jdbc/execute! datasource [stmt])))

(defn drop-schema!
  "Drop tables (for development)"
  []
  (doseq [stmt ["DROP TABLE IF EXISTS post_likes;"
                "DROP TABLE IF EXISTS posts;"
                "DROP TABLE IF EXISTS follows;"
                "DROP TABLE IF EXISTS users;"]]
    (jdbc/execute! datasource [stmt])))
