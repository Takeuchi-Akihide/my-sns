(ns my-sns.worker
  (:require [clojure.core.async :as async :refer [go-loop <! >! chan close! timeout]]
            [my-sns.redis :as redis]
            [my-sns.schema :as schema]))

(def event-chan (chan 1000))

(defn process-event! [event]
  (println "event received:" event)
  (case (:type event)
    :unlike (schema/delete-notification! (:user-id event) (:actor-id event) (:post-id event) "LIKE")
    :like-created
    (let [{:keys [user-id actor-id post-id]} event]
      (when (and user-id (not= actor-id user-id))
        (schema/insert-notification! user-id actor-id post-id "LIKE")
        (redis/publish-notification! user-id actor-id post-id "LIKE")))
    :reply-created
    (let [{:keys [actor-id post-id parent-id]} event
          owner-id (schema/get-user-id-by-post parent-id)]
      (when (and owner-id (not= actor-id owner-id))
        (schema/insert-notification! owner-id actor-id post-id "REPLY")
        (redis/publish-notification! owner-id actor-id post-id "REPLY")))
    :post-created
    (let [{:keys [post-id author-id]} event
          follower-ids (map :users/id (schema/list-all-followers author-id))]
      (doseq [f-id follower-ids]
        (redis/push-to-timeline! f-id post-id))
      (redis/push-to-timeline! author-id post-id))
    (println "Unknown event:" event)))

(defn start-worker []
  (go-loop []
    (when-let [event (<! event-chan)]
      (try
        (process-event! event)
        (catch Exception e
          (println "Failed to process event:" e)))
      (recur))))

(defn start-sync-worker []
  (go-loop []
    (<! (timeout 60000))
    (try
      (let [post-ids (redis/pop-dirty-posts!)]
        (when (seq post-ids)
          (let [like-counts (redis/get-likes-batch post-ids)]
            (doseq [[pid cnt-str] (map vector post-ids like-counts)
                    :when cnt-str]
              (schema/sync-like-count! pid (Long/parseLong cnt-str))))
          (println "Synced" (count post-ids) "posts to DB...")))
      (catch Exception e
        (println "Write-Back failed:" e)))
    (recur)))

(defn start-all-background-services! []
  (println "Initializing background services...")
  (start-worker)
  (start-sync-worker)
  (redis/start-pubsub-listerner!)
  (println "All background services started."))
