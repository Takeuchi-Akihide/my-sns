(ns my-sns.schema-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [buddy.hashers :as hashers]
            [my-sns.schema :as schema]
            [my-sns.test-util :as test-util]))

;; fixtureの設定
(use-fixtures :each test-util/with-test-env)

;; ==============================
;; ユーザー操作のテスト
;; ==============================

(deftest test-add-user
  (let [username "newuser"
        display-name "New User"
        email "newuser@example.com"
        password-hash (hashers/derive "password")]

    (testing "ユーザーを追加できる"
      (schema/add-user! username display-name email password-hash)
      (let [user (schema/get-user-by-username username)]
        (is (not (nil? user)))
        (is (= username (:users/username user)))
        (is (= display-name (:users/display_name user)))
        (is (= email (-> (test-util/query ["SELECT email FROM users WHERE username = ?" username])
                         first
                         :users/email)))))))

(deftest test-get-user-by-username
  (let [username "testuser"
        display-name "Test User"
        email "test@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username display-name email password-hash)

    (testing "ユーザー名からユーザー情報を取得できる"
      (let [user (schema/get-user-by-username username)]
        (is (= username (:users/username user)))
        (is (= display-name (:users/display_name user)))))

    (testing "存在しないユーザー名の場合はnilを返す"
      (is (nil? (schema/get-user-by-username "nonexistent"))))))

(deftest test-get-user-by-id
  (let [username "idtestuser"
        display-name "ID Test User"
        email "idtest@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username display-name email password-hash)
    (let [user (schema/get-user-by-username username)
          user-id (:users/id user)]

      (testing "ユーザーIDからユーザー情報を取得できる"
        (let [fetched-user (schema/get-user-by-id user-id)]
          (is (= user-id (:users/id fetched-user)))
          (is (= username (:users/username fetched-user)))))

      (testing "存在しないユーザーIDの場合はnilを返す"
        (is (nil? (schema/get-user-by-id (java.util.UUID/randomUUID)))))))

  (testing "get-user-by-idがパスワードハッシュを返さない（セキュリティ）"
    (let [username "secureuser"
          display-name "Secure User"
          email "secure@example.com"
          password-hash (hashers/derive "password")]
      (schema/add-user! username display-name email password-hash)
      (let [user (schema/get-user-by-username username)
            user-id (:users/id user)
            fetched-user (schema/get-user-by-id user-id)]
        (is (not (contains? fetched-user :users/password_hash)))))))

(deftest test-update-user
  (let [username "updateuser"
        display-name "Update User"
        email "update@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username display-name email password-hash)
    (let [user (schema/get-user-by-username username)
          user-id (:users/id user)]

      (testing "ユーザー情報を部分更新できる"
        (schema/update-user! user-id {:bio "My bio" :display_name "Updated Name"})
        (let [updated-user (schema/get-user-by-id user-id)]
          (is (= "My bio" (:users/bio updated-user)))
          (is (= "Updated Name" (:users/display_name updated-user))))))))

(deftest test-delete-user
  (let [username "deleteuser"
        display-name "Delete User"
        email "delete@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username display-name email password-hash)
    (let [user (schema/get-user-by-username username)
          user-id (:users/id user)]

      (testing "ユーザーを削除できる"
        (schema/delete-user! user-id)
        (is (nil? (schema/get-user-by-id user-id))))

      (testing "削除されたユーザーは検索できない"
        (is (nil? (schema/get-user-by-username username)))))))

;; ==============================
;; 投稿操作のテスト
;; ==============================

(deftest test-create-post
  (let [username "postuser"
        display-name "Post User"
        email "post@example.com"
        password-hash (hashers/derive "password")
        content "This is a test post"]

    (schema/add-user! username display-name email password-hash)
    (let [user (schema/get-user-by-username username)
          user-id (:users/id user)]

      (testing "投稿を作成できる"
        (let [post (schema/create-post! user-id nil content)]
          (is (not (nil? (:posts/id post))))
          (is (= content (:posts/content post)))
          (is (nil? (:posts/parent_id post)))))

      (testing "返信投稿を作成できる"
        (let [parent-post (schema/create-post! user-id nil "Parent post")
              parent-id (:posts/id parent-post)
              reply-content "This is a reply"
              reply-post (schema/create-post! user-id parent-id reply-content)]
          (is (= parent-id (:posts/parent_id reply-post)))
          (is (= reply-content (:posts/content reply-post))))))))

(deftest test-delete-post
  (let [username "deletepostuser"
        display-name "Delete Post User"
        email "deletepost@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username display-name email password-hash)
    (let [user (schema/get-user-by-username username)
          user-id (:users/id user)
          post (schema/create-post! user-id nil "Post to delete")]

      (testing "投稿を削除できる"
        (schema/delete-post! (:posts/id post))
        (is (nil? (schema/get-user-id-by-post (:posts/id post))))))))

(deftest test-list-posts
  (let [username "listpostuser"
        display-name "List Post User"
        email "listpost@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username display-name email password-hash)
    (let [user (schema/get-user-by-username username)
          user-id (:users/id user)]

      (testing "ユーザーの投稿一覧を取得できる"
        (schema/create-post! user-id nil "Post 1")
        (schema/create-post! user-id nil "Post 2")
        (schema/create-post! user-id nil "Post 3")
        (let [posts (schema/list-posts user-id 10)]
          (is (= 3 (count posts)))
          (is (every? #(= username (:users/username %)) posts)))))))

(deftest test-list-post-replies
  (let [username "replyuser"
        display-name "Reply User"
        email "reply@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username display-name email password-hash)
    (let [user (schema/get-user-by-username username)
          user-id (:users/id user)
          parent-post (schema/create-post! user-id nil "Parent post")
          parent-id (:posts/id parent-post)]

      (testing "投稿の返信一覧を取得できる"
        (schema/create-post! user-id parent-id "Reply 1")
        (schema/create-post! user-id parent-id "Reply 2")
        (let [replies (schema/list-post-replies parent-id 10)]
          (is (= 2 (count replies)))
          (is (every? #(= username (:users/username %)) replies)))))))

;; ==============================
;; フォロー操作のテスト
;; ==============================

(deftest test-follow-user
  (let [username1 "follower"
        username2 "followee"
        display-name1 "Follower"
        display-name2 "Followee"
        email1 "follower@example.com"
        email2 "followee@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username1 display-name1 email1 password-hash)
    (schema/add-user! username2 display-name2 email2 password-hash)

    (let [user1 (schema/get-user-by-username username1)
          user2 (schema/get-user-by-username username2)
          user1-id (:users/id user1)
          user2-id (:users/id user2)]

      (testing "ユーザーをフォローできる"
        (schema/follow-user! user1-id user2-id)
        (let [follows (schema/list-follows user1-id 20 0)]
          (is (= 1 (count follows)))
          (is (= username2 (:users/username (first follows)))))))))

(deftest test-unfollow-user
  (let [username1 "unfollower"
        username2 "unfollowee"
        display-name1 "Unfollower"
        display-name2 "Unfollowee"
        email1 "unfollower@example.com"
        email2 "unfollowee@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username1 display-name1 email1 password-hash)
    (schema/add-user! username2 display-name2 email2 password-hash)

    (let [user1 (schema/get-user-by-username username1)
          user2 (schema/get-user-by-username username2)
          user1-id (:users/id user1)
          user2-id (:users/id user2)]

      (schema/follow-user! user1-id user2-id)

      (testing "ユーザーをアンフォローできる"
        (schema/unfollow-user! user1-id user2-id)
        (let [follows (schema/list-follows user1-id 20 0)]
          (is (= 0 (count follows))))))))

(deftest test-list-follows
  (let [username1 "follower-list"
        username2 "followee-list1"
        username3 "followee-list2"
        display-name1 "Follower List"
        display-name2 "Followee List 1"
        display-name3 "Followee List 2"
        email1 "followerlist@example.com"
        email2 "followeelist1@example.com"
        email3 "followeelist2@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username1 display-name1 email1 password-hash)
    (schema/add-user! username2 display-name2 email2 password-hash)
    (schema/add-user! username3 display-name3 email3 password-hash)

    (let [user1 (schema/get-user-by-username username1)
          user2 (schema/get-user-by-username username2)
          user3 (schema/get-user-by-username username3)
          user1-id (:users/id user1)
          user2-id (:users/id user2)
          user3-id (:users/id user3)]

      (testing "フォロー一覧を取得できる"
        (schema/follow-user! user1-id user2-id)
        (schema/follow-user! user1-id user3-id)
        (let [follows (schema/list-follows user1-id 20 0)]
          (is (= 2 (count follows)))
          (is (contains? (set (map :users/username follows)) username2))
          (is (contains? (set (map :users/username follows)) username3)))))))

(deftest test-list-followers
  (let [username1 "followed-list"
        username2 "follower-list1"
        username3 "follower-list2"
        password-hash (hashers/derive "password")]
    (schema/add-user! username1 "Followed List" "followedlist@example.com" password-hash)
    (schema/add-user! username2 "Follower List 1" "followerlist1@example.com" password-hash)
    (schema/add-user! username3 "Follower List 2" "followerlist2@example.com" password-hash)

    (let [user1-id (:users/id (schema/get-user-by-username username1))
          user2-id (:users/id (schema/get-user-by-username username2))
          user3-id (:users/id (schema/get-user-by-username username3))]
      (testing "フォロワー一覧を取得できる"
        (schema/follow-user! user2-id user1-id)
        (schema/follow-user! user3-id user1-id)
        (let [followers (schema/list-followers user1-id 20 0)]
          (is (= 2 (count followers)))
          (is (contains? (set (map :users/username followers)) username2))
          (is (contains? (set (map :users/username followers)) username3))))
      (testing "全フォロワー一覧を取得できる"
        (let [followers (schema/list-all-followers user1-id)]
          (is (= 2 (count followers)))
          (is (contains? (set (map :users/username followers)) username2))
          (is (contains? (set (map :users/username followers)) username3)))))))

;; ==============================
;; いいね操作のテスト
;; ==============================

(deftest test-like-post
  (let [username "likeuser"
        display-name "Like User"
        email "like@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username display-name email password-hash)
    (let [user (schema/get-user-by-username username)
          user-id (:users/id user)
          post (schema/create-post! user-id nil "Post to like")]

      (testing "投稿にいいねできる"
        (let [ret (schema/like-post! user-id (:posts/id post))]
          (is (= "Liked successfully" (:message ret)))
          (is (= 1 (count (test-util/query ["SELECT * FROM post_likes WHERE user_id = ? AND post_id = ?"
                                            user-id (:posts/id post)])))))))))

(deftest test-unlike-post
  (let [username "unlikeuser"
        display-name "Unlike User"
        email "unlike@example.com"
        password-hash (hashers/derive "password")]

    (schema/add-user! username display-name email password-hash)
    (let [user (schema/get-user-by-username username)
          user-id (:users/id user)
          post (schema/create-post! user-id nil "Post to unlike")]

      (testing "投稿のいいねを解除できる"
        (schema/like-post! user-id (:posts/id post))
        (let [ret (schema/unlike-post! user-id (:posts/id post))]
          (is (= "Unliked successfully" (:message ret)))
          (is (empty? (test-util/query ["SELECT * FROM post_likes WHERE user_id = ? AND post_id = ?"
                                        user-id (:posts/id post)]))))))))

;; ==============================
;; 検索関連のテスト
;; ==============================

(deftest test-search-users
  (let [password-hash (hashers/derive "password")]
    (schema/add-user! "alice" "Alice" "alice@example.com" password-hash)
    (schema/add-user! "bob" "Bob" "bob@example.com" password-hash)
    (schema/add-user! "alison" "Alison" "alison@example.com" password-hash)

    (testing "ユーザー名の接頭辞で検索できる"
      (let [results (schema/search-users "ali" 10)]
        (is (= 2 (count results)))
        (is (every? #(str/starts-with? (:users/username %) "ali") results))))

    (testing "検索結果が制限できる"
      (let [results (schema/search-users "a" 1)]
        (is (= 1 (count results)))))))
