(ns my-sns.handler-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [my-sns.db :as db]
            [my-sns.handler :as handler]
            [my-sns.test-util :as test-util]))

(use-fixtures :each test-util/with-test-env)

(defn parse-body [response]
  (:body response))

(defn ->json [value]
  (cond
    (string? value) (str "\"" (str/escape value {\" "\\\"" \\ "\\\\"}) "\"")
    (keyword? value) (->json (name value))
    (map? value) (str "{"
                      (str/join ","
                                (map (fn [[k v]]
                                       (str (->json (name k)) ":" (->json v)))
                                     value))
                      "}")
    (nil? value) "null"
    (true? value) "true"
    (false? value) "false"
    :else (str value)))

(defn json-request
  ([method path]
   (json-request method path nil nil))
  ([method path body]
   (json-request method path body nil))
  ([method path body token]
   (let [[uri query-string] (str/split path #"\?" 2)
         body-str (when body (->json body))]
     (cond-> {:request-method method
              :uri uri
              :query-string query-string
              :headers {}
              :body (when body-str (io/input-stream (.getBytes body-str "UTF-8")))}
       body (assoc-in [:headers "content-type"] "application/json")
       token (assoc-in [:headers "authorization"] (str "Token " token))))))

(defn call-app [request]
  (handler/base-app request))

(defn create-user! [username]
  (let [response (call-app
                  (json-request :post "/api/v1/users"
                                {:username username
                                 :display_name (str username " display")
                                 :email (str username "@example.com")
                                 :password "password123"}))]
    (is (= 201 (:status response)))
    response))

(defn login-token! [username]
  (let [response (call-app
                  (json-request :post "/api/v1/login"
                                {:username username
                                 :password "password123"}))
        body (parse-body response)]
    (is (= 200 (:status response)))
    (:token body)))

(deftest test-check-required-params
  (testing "認証IDがない場合は例外を発生させる"
    (is (thrown-with-msg? Exception #"Authentication required"
                         (handler/check-required-params nil {}))))

  (testing "必須パラメータが空白の場合は例外を発生させる"
    (is (thrown-with-msg? Exception #"Missing required parameter"
                         (handler/check-required-params "user-id" {"key" ""}))))

  (testing "必須パラメータが存在する場合は例外を発生させない"
    (is (nil? (handler/check-required-params "user-id" {"key" "value"})))))

(deftest test-user-api-success-and-duplicate-error
  (testing "ユーザー作成成功と重複エラー"
    (let [ok-response (call-app
                       (json-request :post "/api/v1/users"
                                     {:username "alice"
                                      :display_name "Alice"
                                      :email "alice@example.com"
                                      :password "password123"}))
          ok-body (parse-body ok-response)
          ng-response (call-app
                       (json-request :post "/api/v1/users"
                                     {:username "alice"
                                      :display_name "Alice 2"
                                      :email "alice2@example.com"
                                      :password "password123"}))
          ng-body (parse-body ng-response)]
      (is (= 201 (:status ok-response)))
      (is (= "User added successfully" (:message ok-body)))
      (is (= 400 (:status ng-response)))
      (is (= "Bad Request" (:error ng-body)))
      (is (= "Username already exists" (:details ng-body))))))

(deftest test-login-api-failure
  (testing "存在しないユーザーでログインできない"
    (let [response (call-app
                    (json-request :post "/api/v1/login"
                                  {:username "nobody"
                                   :password "password123"}))
          body (parse-body response)]
      (is (= 401 (:status response)))
      (is (= "Unauthorized" (:error body)))
      (is (= "Invalid username or password" (:details body))))))

(deftest test-follow-api-success-and-not-found
  (create-user! "follower")
  (create-user! "followee")
  (let [token (login-token! "follower")
        ok-response (call-app
                     (json-request :post "/api/v1/follows/followee" nil token))
        ok-body (parse-body ok-response)
        ng-response (call-app
                     (json-request :post "/api/v1/follows/missing-user" nil token))
        ng-body (parse-body ng-response)]
    (testing "フォロー成功"
      (is (= 201 (:status ok-response)))
      (is (= "Followed successfully" (:message ok-body)))
      (is (= 1 (count (db/query ["SELECT * FROM follows"])))))
    (testing "存在しないユーザーのフォローは404"
      (is (= 404 (:status ng-response)))
      (is (= "Not Found" (:error ng-body))))))

(deftest test-post-api-requires-authentication
  (testing "認証なし投稿は401"
    (let [response (call-app
                    (json-request :post "/api/v1/posts"
                                  {:content "hello"}))
          body (parse-body response)]
      (is (= 401 (:status response)))
      (is (= "Unauthorized" (:error body))))))

(deftest test-post-and-timeline-flow
  (create-user! "timeline-owner")
  (create-user! "timeline-follower")
  (let [owner-token (login-token! "timeline-owner")
        follower-token (login-token! "timeline-follower")]
    (call-app (json-request :post "/api/v1/follows/timeline-owner" nil follower-token))
    (let [post-response (call-app
                         (json-request :post "/api/v1/posts"
                                       {:content "redis timeline post"}
                                       owner-token))
          post-body (parse-body post-response)
          timeline-response (call-app
                             (json-request :get "/api/v1/timeline?limit=10" nil follower-token))
          timeline-body (parse-body timeline-response)]
      (testing "投稿成功"
        (is (= 201 (:status post-response)))
        (is (= "Post created successfully" (:message post-body))))
      (testing "フォロワーのtimelineに投稿が配布される"
        (is (= 200 (:status timeline-response)))
        (is (= 1 (count (:data timeline-body))))
        (is (= "redis timeline post" (-> timeline-body :data first :posts/content)))))))

(deftest test-like-and-unlike-flow
  (create-user! "post-owner")
  (create-user! "liker")
  (let [owner-token (login-token! "post-owner")
        liker-token (login-token! "liker")]
    (call-app (json-request :post "/api/v1/follows/post-owner" nil liker-token))
    (let [post-response (call-app
                         (json-request :post "/api/v1/posts"
                                       {:content "like target"}
                                       owner-token))
          post-id (-> (parse-body post-response) :data :posts/id str)
          like-response (call-app
                         (json-request :post (str "/api/v1/posts/" post-id "/like") nil liker-token))
          like-body (parse-body like-response)
          timeline-response (call-app
                             (json-request :get "/api/v1/timeline?limit=10" nil liker-token))
          timeline-body (parse-body timeline-response)
          unlike-response (call-app
                           (json-request :delete (str "/api/v1/posts/" post-id "/like") nil liker-token))
          unlike-body (parse-body unlike-response)
          timeline-after-unlike (parse-body
                                 (call-app
                                  (json-request :get "/api/v1/timeline?limit=10" nil liker-token)))]
      (testing "like成功"
        (is (= 200 (:status like-response)))
        (is (= "Liked successfully" (:message like-body)))
        (is (= 1 (-> timeline-body :data first :likes-count))))
      (testing "unlike成功"
        (is (= 200 (:status unlike-response)))
        (is (= "Unliked successfully" (:message unlike-body)))
        (is (= 0 (-> timeline-after-unlike :data first :likes-count))))
      (testing "post_likesにも反映される"
        (is (= 0 (count (db/query ["SELECT * FROM post_likes WHERE post_id = ?" (parse-uuid post-id)]))))))))

(deftest test-delete-post-forbidden
  (create-user! "owner")
  (create-user! "intruder")
  (let [owner-token (login-token! "owner")
        intruder-token (login-token! "intruder")
        post-response (call-app
                       (json-request :post "/api/v1/posts"
                                     {:content "private post"}
                                     owner-token))
        post-id (-> (parse-body post-response) :data :posts/id str)
        response (call-app
                  (json-request :delete (str "/api/v1/posts/" post-id) nil intruder-token))
        body (parse-body response)]
    (testing "他人の投稿削除は403"
      (is (= 403 (:status response)))
      (is (= "Forbidden" (:error body)))
      (is (= "You are not authorized to delete this post" (:details body))))))

(deftest test-invalid-post-id-returns-bad-request
  (create-user! "invalid-id-user")
  (let [token (login-token! "invalid-id-user")
        response (call-app
                  (json-request :post "/api/v1/posts/not-a-uuid/like" nil token))
        body (parse-body response)]
    (testing "不正なUUID形式は400"
      (is (= 400 (:status response)))
      (is (= "Bad Request" (:error body))))))
