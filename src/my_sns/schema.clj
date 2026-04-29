(ns my-sns.schema
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [my-sns.db :refer [*current-db* with-master]]
            [my-sns.redis :as redis]))

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
      likes_count INTEGER DEFAULT 0,
      replies_count INTEGER DEFAULT 0,
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
   "CREATE INDEX IF NOT EXISTS idx_follows_follower_id ON follows(follower_id);"

   "CREATE TABLE IF NOT EXISTS post_likes (
      user_id UUID REFERENCES users(id) ON DELETE CASCADE,
      post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (user_id, post_id)
    );"

   "CREATE UNIQUE INDEX IF NOT EXISTS idx_post_likes ON post_likes(post_id, user_id);"
   "CREATE INDEX IF NOT EXISTS idx_posts_timeline ON posts (parent_id, created_at DESC, id DESC);"

   "CREATE TABLE IF NOT EXISTS notifications (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      actor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
      type VARCHAR(50) NOT NULL,
      is_read BOOLEAN DEFAULT FALSE,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );"
   "CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id, created_at DESC);"
   "CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_upsert_target ON notifications(user_id, actor_id, post_id, type) WHERE is_read = FALSE;"])

(defn add-user!
  [username display-name email password-hash]
  (with-master
    (jdbc/execute! *current-db*
                   ["INSERT INTO users (username, display_name, email, password_hash) VALUES (?, ?, ?, ?)"
                    username display-name email password-hash])))

(defn get-user-by-username
  [username]
  (jdbc/execute-one! *current-db*
                     ["SELECT id, username, display_name, bio, created_at, updated_at
                       FROM users WHERE username = ?"
                      username]))

(defn get-user-by-id
  [user-id]
  (jdbc/execute-one! *current-db*
                     ["SELECT id, username, display_name, bio, created_at, updated_at
                       FROM users WHERE id = ?"
                      user-id]))

(defn get-password-hash-by-id
  [user-id]
  (-> (jdbc/execute-one! *current-db*
                         ["SELECT password_hash FROM users WHERE id = ?"
                          user-id])
      :users/password_hash))

(defn get-user-id-by-post
  [post-id]
  (-> (jdbc/execute-one! *current-db*
                         ["SELECT user_id FROM posts WHERE id = ?"
                          post-id])
      :posts/user_id))

(defn search-users
  [username-prefix limit]
  (jdbc/execute! *current-db*
                 ["SELECT id, username, bio FROM users WHERE username LIKE ? ORDER BY username LIMIT ?"
                  (str username-prefix "%") limit]))

(defn update-user!
  [user-id updates-map]
  (with-master
    (sql/update! *current-db*
                 :users
                 (assoc updates-map :updated_at (java.time.OffsetDateTime/now))
                 {:id user-id})))

(defn delete-user!
  [user-id]
  (with-master
    (jdbc/execute! *current-db*
                   ["DELETE FROM users WHERE id = ?"
                    user-id])))

(defn create-post!
  [user-id parent-id content]
  (with-master
    (jdbc/with-transaction [tx *current-db*]
      (let [created-post (jdbc/execute-one! tx
                                            ["INSERT INTO posts (user_id, parent_id, content) VALUES (?, ?, ?) RETURNING *"
                                             user-id parent-id content])]
        (when parent-id
          (jdbc/execute! tx
                         ["UPDATE posts SET replies_count = replies_count + 1 WHERE id = ?"
                          parent-id]))
        created-post))))

(defn delete-post!
  [post-id]
  (with-master
    (jdbc/with-transaction [tx *current-db*]
      (let [post (jdbc/execute-one! tx ["SELECT parent_id FROM posts WHERE id = ?" post-id])]
        (when (:posts/parent_id post)
        (jdbc/execute! tx ["UPDATE posts SET replies_count = GREATEST(replies_count - 1, 0) WHERE id = ?" (:posts/parent_id post)]))
      (jdbc/execute! tx ["DELETE FROM posts WHERE id = ?" post-id])))))

(defn get-posts-by-ids [my-uuid post-ids]
  (jdbc/execute! *current-db*
                 ["SELECT p.id, p.content, p.created_at, p.user_id, p.replies_count, 
                          u.username, u.display_name, u.bio,
                          EXISTS (SELECT 1 FROM post_likes WHERE post_id = p.id AND user_id = ?) as is_liked
                   FROM unnest(?::uuid[]) WITH ORDINALITY AS t(id, ord)
                   JOIN posts p ON p.id = t.id
                   JOIN users u ON p.user_id = u.id
                   ORDER BY t.ord"
                  my-uuid
                  (into-array java.util.UUID post-ids)]))

(defn follow-user!
  [follower-id followed-id]
  (with-master
    (jdbc/execute! *current-db*
                   ["INSERT INTO follows (follower_id, followed_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
                    follower-id followed-id])))

(defn unfollow-user!
  [follower-id followed-id]
  (with-master
    (jdbc/execute! *current-db*
                   ["DELETE FROM follows WHERE follower_id = ? AND followed_id = ?"
                    follower-id followed-id])))

(defn list-posts
  [user-id limit]
  (jdbc/execute! *current-db*
                 ["SELECT p.id, p.content, p.created_at, u.username
                   FROM posts p
                   JOIN users u ON p.user_id = u.id
                   WHERE p.user_id = ?
                   ORDER BY p.created_at DESC
                   LIMIT ?"
                  user-id limit]))

(defn list-follows
  [user-id]
  (jdbc/execute! *current-db*
                 ["SELECT u.id, u.username
                   FROM follows f
                   JOIN users u ON f.followed_id = u.id
                   WHERE f.follower_id = ?"
                  user-id]))

(defn list-followers
  [user-id]
  (jdbc/execute! *current-db*
                 ["SELECT u.id, u.username
                   FROM follows f
                   JOIN users u ON f.follower_id = u.id
                   WHERE f.followed_id = ?"
                  user-id]))

(defn like-post!
  [user-id post-id]
  (with-master
    (let [inserted? (jdbc/execute-one! *current-db*
                                       ["INSERT INTO post_likes (user_id, post_id) 
                                       VALUES (?, ?) ON CONFLICT DO NOTHING"
                                        user-id post-id])]
      (if (pos? (:next.jdbc/update-count inserted?))
        (do (redis/incr-like-count post-id)
            (redis/mark-dirty! post-id)
            {:status 200 :message "Liked successfully"})
        {:status 200 :message "Already liked"}))))

(defn unlike-post!
  [user-id post-id]
  (with-master
    (let [deleted-result (first (jdbc/execute! *current-db*
                                               ["DELETE FROM post_likes WHERE user_id = ? AND post_id = ?"
                                                user-id post-id]))]
      (if (and deleted-result (pos? (:next.jdbc/update-count deleted-result)))
        (do (redis/decr-like-count post-id)
            (redis/mark-dirty! post-id)
            {:status 200 :message "Unliked successfully"})
        {:status 200 :message "Not liked"}))))

(defn sync-like-count!
  [post-id current-count]
  (with-master
    (jdbc/execute! *current-db*
                   ["UPDATE posts SET likes_count = ? WHERE id = ?"
                    current-count post-id])))

(defn list-post-replies
  ;; 1つ下の階層の子投稿を取得する。これを使いまわすことでリプライ一覧が取得可能なはず。
  [post-id limit]
  (jdbc/execute! *current-db*
                 ["SELECT p.id, p.content, p.created_at, u.username
                    FROM posts p
                    JOIN users u ON p.user_id = u.id
                    WHERE p.parent_id = ?
                    ORDER BY p.created_at ASC
                    LIMIT ?"
                  post-id limit]))

(defn insert-notification!
  [user-id actor-id post-id type]
  (with-master
    (jdbc/execute! *current-db*
                   ["INSERT INTO notifications (user_id, actor_id, post_id, type) VALUES (?, ?, ?, ?)
                     ON CONFLICT (user_id, actor_id, post_id, type) WHERE is_read = FALSE 
                     DO UPDATE SET created_at = CURRENT_TIMESTAMP"
                    user-id actor-id post-id type])))

(defn delete-notification!
  [user-id actor-id post-id type]
  (with-master
    (jdbc/execute! *current-db*
                   ["DELETE FROM notifications WHERE user_id = ? AND actor_id = ? AND post_id = ? AND type = ?"
                    user-id actor-id post-id type])))

(defn create-schema!
  []
  (with-master
    (doseq [stmt ddl-statements]
      (jdbc/execute! *current-db* [stmt]))))

(defn drop-schema!
  "Drop tables (for development)"
  []
  (doseq [stmt ["DROP TABLE IF EXISTS notifications;"
                "DROP TABLE IF EXISTS post_likes;"
                "DROP TABLE IF EXISTS posts;"
                "DROP TABLE IF EXISTS follows;"
                "DROP TABLE IF EXISTS users;"]]
    (with-master
      (jdbc/execute! *current-db* [stmt]))))
