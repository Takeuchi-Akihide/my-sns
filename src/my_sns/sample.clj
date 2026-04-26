(ns my-sns.sample
  (:require [clojure.java.io :as io]
            [aero.core :as aero]
            [buddy.hashers :as hashers]
            [my-sns.schema :as schema]))

(defn load-sample-data
  "Load sample data from edn file and insert into database"
  []
  (println "Loading sample data...")
  (try
    (schema/create-schema!)

    (let [sample-data (aero/read-config (io/resource "sample-data.edn"))
          users (:users sample-data)
          posts (:posts sample-data)
          follows (:follows sample-data)
          likes (:likes sample-data)]

      ;; Create users
      (println "Creating users...")
      (doseq [user users]
        (try
          (let [hashed-password (hashers/derive (:password user))]
            (schema/add-user! (:username user) (:display-name user) (:email user) hashed-password)
            (println (str "  Created user: " (:username user))))
          (catch Exception e
            (println (str "  User already exists or error: " (:username user) " - " (.getMessage e))))))

      ;; Create posts
      (println "Creating posts...")
      (let [created-posts (atom [])]  ; 作成した投稿を保持
        (let [user-posts (group-by :username posts)]
          (doseq [[username user-posts] user-posts]
            (when-let [user (schema/get-user-by-username username)]
              (let [user-id (:users/id user)]
                (doseq [post user-posts]
                  (try
                    (let [created-post (schema/create-post! user-id nil (:content post))]
                      (swap! created-posts conj created-post)  ; 作成した投稿を保存
                      (println (str "  Created post for " username ": " (subs (:content post) 0 (min 30 (count (:content post)))) "...")))
                    (catch Exception e
                      (println (str "  Error creating post for " username ": " (.getMessage e))))))))))

        ;; Create follows
        (println "Creating follow relationships...")
        (doseq [[follower followed] follows]
          (try
            (when-let [follower-user (schema/get-user-by-username follower)]
              (when-let [followed-user (schema/get-user-by-username followed)]
                (schema/follow-user! (:users/id follower-user) (:users/id followed-user))
                (println (str "  " follower " follows " followed))))
            (catch Exception e
              (println (str "  Error creating follow: " follower " -> " followed ": " (.getMessage e)))))))

      (println "Sample data loaded successfully!"))
    (catch Exception e
      (println (str "Error loading sample data: " (.getMessage e))))))
