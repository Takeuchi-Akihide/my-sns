(ns my-sns.worker
  (:require [clojure.core.async :as async :refer [go-loop <! >! chan close!]]
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
