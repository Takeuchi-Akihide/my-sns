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
          follows (:follows sample-data)]
      ;; Create users
      (println "Creating users...")
      (doseq [user users]
        (try
          (let [hashed-password (hashers/derive (:password user))]
            (schema/add-user! (:username user) (:display-name user) (:email user) hashed-password)
            (println (str "  Created user: " (:username user))))
          (catch Exception e
            (println (str "  User already exists or error: " (:username user) " - " (.getMessage e))))))

      ;; Create follows
      (println "Creating follow relationships...")
      (doseq [[follower followed] follows]
        (try
          (when-let [follower-user (schema/get-user-by-username follower)]
            (when-let [followed-user (schema/get-user-by-username followed)]
              (schema/follow-user! (:users/id follower-user) (:users/id followed-user))
              (println (str "  " follower " follows " followed))))
          (catch Exception e
            (println (str "  Error creating follow: " follower " -> " followed ": " (.getMessage e))))))

      (println "Sample data loaded successfully!"))
    (catch Exception e
      (println (str "Error loading sample data: " (.getMessage e))))))
