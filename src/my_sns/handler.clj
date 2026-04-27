(ns my-sns.handler
  (:require [clojure.string :as str]
            [clojure.core.async :as async]
            [reitit.ring :as ring]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [buddy.hashers :as hashers]
            [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [my-sns.handler.auth :as auth]
            [my-sns.redis :as redis]
            [my-sns.schema :as schema]
            [my-sns.worker :as worker]))

(def LIMIT 20)

(defn parse-uuid-param [value]
  (or (parse-uuid value)
      (throw (ex-info "Invalid id format. Must be UUID."
                      {:type :bad-request}))))

(defn wrap-global-exception-handling [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (let [ex-map (ex-data e)
              err-type (:type ex-map)
              err-msg  (.getMessage e)]
          (cond
            (= err-type :bad-request)
            {:status 400 :body {:error "Bad Request" :details err-msg}}

            (= err-type :unauthorized)
            {:status 401 :body {:error "Unauthorized" :details err-msg}}

            (= err-type :forbidden)
            {:status 403 :body {:error "Forbidden" :details err-msg}}

            (= err-type :not-found)
            {:status 404 :body {:error "Not Found" :details err-msg}}

            :else
            ;; 想定外の例外（DB接続エラー、NullPointer等）は一律500を返す
            (do
              (println "[ERROR] Internal Server Error:" err-msg)
              (.printStackTrace e)
              {:status 500 :body {:error "Internal Server Error" :details "Please try again later"}})))))))

(defn wrap-parse-identity-uuid [handler]
  (fn [req]
    (if-let [user-id-str (-> req :identity :user_id)]
      (handler (assoc-in req [:identity :user_id] (parse-uuid user-id-str)))
      (handler req))))

(defn check-required-params [id params]
  (when-not id
    (throw (ex-info "Authentication required" {:type :unauthorized})))
  (doseq [[key param] params]
    (when (str/blank? param)
      (throw (ex-info (str "Missing required parameter: " key) {:type :bad-request})))))

(defn post-posts-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        body (:body req)
        parent-id (:parent_id body)
        content (:content body)]
    (check-required-params my-uuid {"content" content})

    (if (schema/get-user-by-id my-uuid)
      (let [user-id my-uuid
            parent-id (when parent-id
                        (parse-uuid-param parent-id))
            res (schema/create-post! user-id parent-id content)]
        (if parent-id
          (async/put! worker/event-chan {:type :reply-created
                                         :actor-id my-uuid
                                         :post-id (:posts/id res)
                                         :parent-id parent-id})
          (async/put! worker/event-chan {:type :post-created
                                         :author-id my-uuid
                                         :post-id (:posts/id res)}))
        {:status 201
         :body {:message "Post created successfully" :data res}})
      (throw (ex-info "User not found:" {:type :not-found})))))

(defn delete-posts-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        post-id (-> req :path-params :post_id)]
    (check-required-params my-uuid {"post_id" post-id})

    (try
      (let [post-uuid (parse-uuid-param post-id)
            user-id (schema/get-user-id-by-post post-uuid)]
        (if (= my-uuid user-id)
          (do
            (schema/delete-post! post-uuid)
            {:status 200
             :body {:message "Post deleted successfully"}})
          (throw (ex-info "You are not authorized to delete this post" {:type :forbidden}))))
      (catch IllegalArgumentException _
        (throw (ex-info "Invalid Post ID format. Must be UUID." {:type :bad-request}))))))

(defn list-posts-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        target-user (-> req :params :username)
        limit (Long/parseLong (or (-> req :params :limit) (str LIMIT)))]
    (check-required-params my-uuid {})

    (if (schema/get-user-by-id my-uuid)
      (if-let [user (schema/get-user-by-username target-user)]
        (let [user-id (:users/id user)
              posts (schema/list-posts user-id limit)]
          {:status 200
           :body {:message "Posts retrieved successfully" :data posts}})
        (throw (ex-info (str "Target user not found: " target-user) {:type :not-found})))
      (throw (ex-info "User account no longer exists. Please log in again." {:type :unauthorized})))))

(defn list-follows-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        target-user (-> req :params :username)]
    (check-required-params my-uuid {})

    (if (schema/get-user-by-id my-uuid)
      (if-let [user (schema/get-user-by-username target-user)]
        (let [user-id (:users/id user)
              follows (schema/list-follows user-id)]
          {:status 200
           :body {:message "Followers retrieved successfully" :data follows}})
        (throw (ex-info (str "Target user not found: " target-user) {:type :not-found})))
      (throw (ex-info "User account no longer exists. Please log in again." {:type :unauthorized})))))

(defn post-follow-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        target-username (-> req :path-params :target_username)]
    (check-required-params my-uuid {})

    (if (schema/get-user-by-id my-uuid)
      (if-let [target-user (schema/get-user-by-username target-username)]
        (do
          (schema/follow-user! my-uuid (:users/id target-user))
          {:status 201
           :body {:message "Followed successfully" :data target-username}})
        (throw (ex-info (str "Target user not found: " target-username) {:type :not-found})))

      (throw (ex-info "User account no longer exists. Please log in again." {:type :unauthorized})))))

(defn delete-follow-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        target-username (-> req :path-params :target_username)]
    (check-required-params my-uuid {})

    (if (schema/get-user-by-id my-uuid)
      (if-let [target-user (schema/get-user-by-username target-username)]
        (do
          (schema/unfollow-user! my-uuid (:users/id target-user))
          {:status 201
           :body {:message "Unfollowed successfully" :data target-username}})
        (throw (ex-info (str "Target user not found: " target-username) {:type :not-found})))

      (throw (ex-info "User account no longer exists. Please log in again." {:type :unauthorized})))))

(defn list-timeline-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        params (:params req)
        limit  (Long/parseLong (get params :limit (str LIMIT)))
        offset (Long/parseLong (get params :offset "0"))
        start offset
        end (+ offset limit -1)]
    (check-required-params my-uuid {})

    (if (schema/get-user-by-id my-uuid)
      (let [post-ids (redis/get-timeline-ids my-uuid start end)]
        (if (empty? post-ids)
          {:status 200 :body {:data []}}
          (let [posts (schema/get-posts-by-ids my-uuid post-ids)
                post-ids-in-db (map :posts/id posts)
                likes-counts (redis/get-likes-batch post-ids-in-db)
                final-posts (map (fn [post cnt-str]
                                   (assoc post :likes-count (if cnt-str (Long/parseLong cnt-str) 0)))
                                 posts likes-counts)]
            {:status 200
             :body {:data final-posts}})))
      (throw (ex-info "User account no longer exists. Please log in again." {:type :unauthorized})))))

(defn post-user-handler [req]
  (let [{:keys [username email password] display-name :display_name} (:body req)]
    (when (some str/blank? [username email password display-name])
      (throw (ex-info "Username, display name, email and password cannot be empty" {:type :bad-request})))

    (if (schema/get-user-by-username username)
      (throw (ex-info "Username already exists" {:type :bad-request}))
      (let [hashed-password (hashers/derive password)
            res (schema/add-user! username display-name email hashed-password)]
        {:status 201
         :body {:message "User added successfully" :data res}}))))

(defn get-user-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        target-user (-> req :path-params :username)]
    (check-required-params my-uuid {})

    (if (schema/get-user-by-id my-uuid)
      (if-let [user (schema/get-user-by-username target-user)]
        {:status 200
         :body {:message "User retrieved successfully" :data user}}
        (throw (ex-info (str "Target user not found: " target-user) {:type :not-found})))
      (throw (ex-info "User account no longer exists. Please log in again." {:type :unauthorized})))))

(defn put-user-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        body (:body req)
        raw-updates (into {} (remove (comp nil? val)
                                     (select-keys body [:username :email :password :bio :display_name])))
        updates (if (:password raw-updates)
                  (-> raw-updates
                      (assoc :password_hash (hashers/derive (:password raw-updates)))
                      (dissoc :password))
                  raw-updates)]
    (check-required-params my-uuid {})
    (when (empty? updates)
      (throw (ex-info "No valid fields provided for update" {:type :bad-request})))

    (if-let [current-user (schema/get-user-by-id my-uuid)]
      (do
        (when-let [existing-user (and (:username updates)
                                      (schema/get-user-by-username (:username updates)))]
          (when (not= (:users/id existing-user) (:users/id current-user))
            (throw (ex-info "Username already exists" {:type :bad-request}))))
        (let [res (schema/update-user! my-uuid updates)]
          {:status 200
           :body {:message "User updated successfully" :data res}}))

      (throw (ex-info "User account no longer exists. Please log in again." {:type :unauthorized})))))

(defn delete-user-handler [req]
  (let [my-uuid (-> req :identity :user_id)]
    (check-required-params my-uuid {})

    (if-let [user (schema/get-user-by-id my-uuid)]
      (do
        (schema/delete-user! my-uuid)
        {:status 200
         :body {:message "User deleted successfully" :data (:users/id user)}})
      (throw (ex-info "User account no longer exists. Please log in again." {:type :unauthorized})))))

(defn post-like-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        post-id (-> req :path-params :post_id)]
    (check-required-params my-uuid {"post_id" post-id})

    (if (schema/get-user-by-id my-uuid)
      (try
        (let [post-uuid (parse-uuid-param post-id)
              post-owner-id (schema/get-user-id-by-post post-uuid)
              ret (schema/like-post! my-uuid post-uuid)]
          (async/put! worker/event-chan {:type :like
                                         :user-id post-owner-id
                                         :actor-id my-uuid
                                         :post-id post-uuid})
          {:status 200
           :body {:message (:message ret)}})
        (catch IllegalArgumentException _
          (throw (ex-info "Invalid Post ID format. Must be UUID." {:type :bad-request}))))
      (throw (ex-info "User account no longer exists. Please log in again." {:type :unauthorized})))))

(defn post-unlike-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        post-id (-> req :path-params :post_id)]
    (check-required-params my-uuid {"post_id" post-id})

    (if (schema/get-user-by-id my-uuid)
      (try
        (let [post-uuid (parse-uuid-param post-id)
              post-owner-id (schema/get-user-id-by-post post-uuid)
              ret (schema/unlike-post! my-uuid post-uuid)]
          (async/put! worker/event-chan {:type :unlike
                                         :user-id post-owner-id
                                         :actor-id my-uuid
                                         :post-id post-uuid})
          {:status 200
           :body {:message (:message ret)}})
        (catch IllegalArgumentException _
          (throw (ex-info "Invalid Post ID format. Must be UUID." {:type :bad-request}))))
      (throw (ex-info "User account no longer exists. Please log in again." {:type :unauthorized})))))

(defn list-posts-replies-handler [req]
  (let [my-uuid (-> req :identity :user_id)
        post-id (-> req :path-params :post_id)
        limit (Long/parseLong (or (-> req :params :limit) (str LIMIT)))]
    (check-required-params my-uuid {"post_id" post-id})

    (if (schema/get-user-by-id my-uuid)
      (try
        (let [post-uuid (parse-uuid-param post-id)
              replies (schema/list-post-replies post-uuid limit)]
          {:status 200
           :body {:message "Replies retrieved successfully" :data replies}})
        (catch IllegalArgumentException _
          (throw (ex-info "Invalid Post ID format. Must be UUID." {:type :bad-request}))))
      (throw (ex-info "User account no longer exists. Please log in again." {:type :unauthorized})))))

(def auth-backend (jws-backend {:secret auth/secret}))

(def base-app
  (-> (ring/ring-handler
       (ring/router
        ["/api/v1"
         {:middleware [[wrap-authentication auth-backend]
                       wrap-parse-identity-uuid]}
         ["/login"     {:post auth/login-handler}]
         ["/users"
          ;; POST/PUT/DELETE: /api/v1/users
          [""           {:post post-user-handler
                         :put put-user-handler
                         :delete delete-user-handler}]
          ;; GET: /api/v1/users/user1
          ["/:username" {:get get-user-handler}]]
         ["/posts"
          ;; GET:  /api/v1/posts?username=user1
          ;; POST: /api/v1/posts
          [""          {:get list-posts-handler
                        :post post-posts-handler}]
          ;; DELETE: /api/v1/posts/456
          ["/:post_id" {:get list-posts-replies-handler
                        :delete delete-posts-handler}]
          ["/:post_id/like" {:post post-like-handler
                             :delete post-unlike-handler}]]
         ["/follows"
          ;; GET: /api/v1/follows?username=user1
          [""                 {:get list-follows-handler}]
          ;; POST/DELETE: /api/v1/follows/user1
          ["/:target_username" {:post post-follow-handler
                                :delete delete-follow-handler}]]
         ["/timeline"      {:get list-timeline-handler}]])
       (ring/routes
        (ring/create-resource-handler {:path "/"})
       (ring/create-default-handler {:not-found (constantly {:status 404 :body "Not found"})})))
      wrap-global-exception-handling
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :post :put :delete]
                 :access-control-allow-headers ["Content-Type"])
      (wrap-json-body {:keywords? true})
      wrap-keyword-params
      wrap-params))

(def app
  (wrap-json-response base-app))
