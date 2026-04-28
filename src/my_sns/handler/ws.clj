(ns my-sns.handler.ws
  (:require [org.httpkit.server :as http-kit]
            [clojure.data.json :as json]))

(def connected-users (atom {}))

(defn ws-handler [req]
  (let [user-id (-> req :identity :user_id str)]
    (http-kit/with-channel req channel
      (swap! connected-users assoc user-id channel)

      (http-kit/on-close channel
                         (fn [_status]
                           (swap! connected-users dissoc user-id)
                           (println "User disconnected from WS:" user-id))))))

(defn send-notification-to-user! [user-id message-map]
  (when-let [channel (get @connected-users (str user-id))]
    (http-kit/send! channel (json/write-str message-map))))
