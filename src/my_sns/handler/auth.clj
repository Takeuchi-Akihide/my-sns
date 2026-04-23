(ns my-sns.handler.auth
  (:require [buddy.sign.jwt :as jwt]
            [buddy.hashers :as hashers]
            [my-sns.schema :as schema]))

;; 本番環境では必ず環境変数から取得してください（ハードコーディング厳禁）
(def secret "super-secret-key-for-my-sns")

;; (defn verify-password [raw-password hashed-password]
;;   (hashers/check raw-password hashed-password))

(defn login-handler [req]
  (let [{:keys [username password]} (:body req)
        user (schema/get-user-by-username username)]

    (if (and user (hashers/check password (:users/password_hash user)))
      (let [token (jwt/sign {:user_id (:users/id user)
                             :exp (.getTime (java.util.Date. (+ (System/currentTimeMillis) (* 1000 60 60 24))))} ;; 1日有効
                            secret)]
        {:status 200 :body {:token token}})
      (throw (ex-info "Invalid username or password" {:type :unauthorized})))))
