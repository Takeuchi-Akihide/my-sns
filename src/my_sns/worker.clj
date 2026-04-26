(ns my-sns.worker
  (:require [clojure.core.async :as async :refer [go-loop <! >! chan close! timeout]]
            [my-sns.redis :as redis]
            [my-sns.schema :as schema]))

(def event-chan (chan 1000))

(defn start-worker []
  (go-loop []
    (when-let [event (<! event-chan)]
      (try
        (println "event received:" event)
        (case (:type event)
          :like (schema/insert-notification! (:user-id event) (:actor-id event) (:post-id event) "LIKE")
          :unlike (schema/delete-notification! (:user-id event) (:actor-id event) (:post-id event) "LIKE")
          ;; 他のイベントタイプもここで処理可能
          (println "Unknown event:" event))
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
