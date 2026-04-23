(ns my-sns.handler
  (:require [clojure.string :as str]
            [reitit.ring :as ring]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [buddy.hashers :as hashers]
            [my-sns.schema :as schema]))

(def LIMIT 20)

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

            (= err-type :not-found)
            {:status 404 :body {:error "Not Found" :details err-msg}}

            :else
            ;; 想定外の例外（DB接続エラー、NullPointer等）は一律500を返す
            (do
              (println "[ERROR] Internal Server Error:" err-msg)
              (.printStackTrace e)
              {:status 500 :body {:error "Internal Server Error" :details "Please try again later"}})))))))

(defn check-required-params [id params]
  (when-not id
    (throw (ex-info "Authentication required" {:type :unauthorized})))
  (doseq [[key param] params]
    (when (str/blank? param)
      (throw (ex-info (str "Missing required parameter: " key) {:type :bad-request})))))

(defn post-posts-handler [req]
  (let [my-uuid "uuid"
        body (:body req)
        parent-id (:parent_id body)
        content (:content body)]
    (check-required-params my-uuid {"content" content})

    (let [user-id (parse-uuid my-uuid)
          parent-id (parse-uuid (or parent-id ""))
          res (schema/create-post! user-id parent-id content)]
      {:status 201
       :body {:message "Post created successfully" :data res}})))

(defn delete-posts-handler [req]
  (let [my-uuid "uuid"
        post-id (-> req :path-params :post_id)]
    (check-required-params my-uuid {"post_id" post-id})

    (try
      (let [post-uuid (parse-uuid post-id)]
        (schema/delete-post! post-uuid)
        {:status 200
         :body {:message "Post deleted successfully"}})
      (catch IllegalArgumentException _
        (throw (ex-info "Invalid Post ID format. Must be UUID." {:type :bad-request}))))))

(defn list-posts-handler [req]
  (let [my-uuid "uuid"
        target-user (-> req :query-params :username)
        limit (Long/parseLong (or (-> req :query-params :limit) (str LIMIT)))]
    (check-required-params my-uuid {})

    (if-let [user (schema/get-user-by-username target-user)]
      (let [user-id (:users/id user)
            posts (schema/list-posts user-id limit)]
        {:status 200
         :body {:message "Posts retrieved successfully" :data posts}})
      (throw (ex-info (str "Target user not found: " target-user) {:type :not-found})))))

(defn list-follows-handler [req]
  (let [my-uuid "uuid"]
    (check-required-params my-uuid {})

    (let [user-id my-uuid
          follows (schema/list-follows user-id)]
      {:status 200
       :body {:message "Followers retrieved successfully" :data follows}})))

(defn post-follow-handler [req]
  (let [my-uuid "uuid"
        target-username (-> req :path-params :target_username)]
    (check-required-params my-uuid {"target_username" target-username})

    (if-let [target-user (schema/get-user-by-username target-username)]
      (do
        (schema/follow-user! my-uuid (:users/id target-user))
        {:status 201
         :body {:message "Followed successfully" :data target-username}})
      (throw (ex-info (str "Target user not found: " target-username) {:type :not-found})))))

(defn delete-follow-handler [req]
  (let [my-uuid "uuid"
        target-username (-> req :path-params :target_username)]
    (check-required-params my-uuid {"target_username" target-username})

    (if-let [target-user (schema/get-user-by-username target-username)]
      (do
        (schema/unfollow-user! my-uuid (:users/id target-user))
        {:status 201
         :body {:message "Unfollowed successfully" :data target-username}})
      (throw (ex-info (str "Target user not found: " target-username) {:type :not-found})))))


(defn list-timeline-handler [req]
  (let [my-uuid "uuid"
        limit (Long/parseLong (or (-> req :query-params :limit) (str LIMIT)))]
    (check-required-params my-uuid {})

    (let [user-id my-uuid
          timeline (schema/list-timeline user-id limit)]
      {:status 200
       :body {:message "Timeline retrieved successfully" :data timeline}})))

(defn post-user-handler [req]
  (let [{:keys [username email password]} (:body req)
        display-name (:display_name (:body req))]
    (when (or (str/blank? username) (str/blank? email) (str/blank? password) (str/blank? display-name))
      (throw (ex-info "Username, display name, email and password cannot be empty" {:type :bad-request})))

    (if (schema/get-user-by-username username)
      (throw (ex-info "Username already exists" {:type :bad-request}))
      (let [hashed-password (hashers/derive password)
            res (schema/add-user! username display-name email hashed-password)]
        {:status 201
         :body {:message "User added successfully" :data res}}))))

(defn get-user-handler [req]
  (let [my-uuid "uuid"]
    (check-required-params my-uuid {})

    (if-let [user (schema/get-user-by-id my-uuid)]
      {:status 200
       :body {:message "User retrieved successfully" :data user}}
      (throw (ex-info "User not found" {:type :not-found})))))

(defn put-user-handler [req]
  (let [my-uuid "uuid"
        {:keys [username email password bio]} (:body req)
        display-name (:display_name (:body req))]
    (check-required-params my-uuid {"username" username
                                    "email" email
                                    "display_name" display-name
                                    "password" password
                                    "bio" bio})

    (if (schema/get-user-by-id my-uuid)
      ;; usernameの重複をチェックする
      (let [res (schema/update-user! my-uuid username display-name email (hashers/derive password) bio)]
        {:status 200
         :body {:message "User updated successfully" :data res}})
      (throw (ex-info "User not found" {:type :not-found})))))

(defn delete-user-handler [req]
  (let [my-uuid "uuid"]
    (check-required-params my-uuid {})

    (schema/delete-user! my-uuid)
    {:status 200
     :body {:message "User deleted successfully" :data my-uuid}}))

(def app
  (-> (ring/ring-handler
       (ring/router
        ["/api/v1"
         ["/users"
          ;; POST: /api/v1/users
          [""           {:post post-user-handler}]
          ;; GET/PUT/DELETE: /api/v1/users/user1
          ["/:username" {:get get-user-handler
                         :put put-user-handler
                         :delete delete-user-handler}]]
         ["/posts"
          ;; GET:  /api/v1/posts?username=user1
          ;; POST: /api/v1/posts
          [""          {:get list-posts-handler
                        :post post-posts-handler}]
          ;; DELETE: /api/v1/posts/456
          ["/:post_id" {:delete delete-posts-handler}]]
         ["/timeline"      {:get list-timeline-handler}]])
       (ring/routes
        (ring/create-resource-handler {:path "/"})
        (ring/create-default-handler {:not-found (constantly {:status 404 :body "Not found"})})))
      wrap-global-exception-handling
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :post :put :delete]
                 :access-control-allow-headers ["Content-Type"])
      wrap-json-response
      (wrap-json-body {:keywords? true})
      wrap-keyword-params
      wrap-params))
