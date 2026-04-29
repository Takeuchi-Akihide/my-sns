(ns my-sns.redis
  (:require [clojure.data.json :as json]
            [taoensso.carmine :as car]
            [my-sns.handler.ws :as ws]))

(def server1-conn {:pool {} :spec {:uri "redis://localhost:6379"}})
(def BATCH-SIZE 100)
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

(defn incr-like-count [post-id]
  (let [key (str "post:" post-id ":likes")]
    (wcar* (car/incr key))))

(defn decr-like-count [post-id]
  (let [key (str "post:" post-id ":likes")]
    (wcar* (car/decr key))))

(defn get-likes-batch [post-ids]
  (if (empty? post-ids)
    []
    (wcar* (apply car/mget (map #(str "post:" % ":likes") post-ids)))))

(defn mark-dirty! [post-id]
  (wcar* (car/sadd "dirty_posts" post-id)))

(defn pop-dirty-posts! []
  (wcar* (car/spop "dirty_posts" BATCH-SIZE)))

(defn push-to-timeline! [user-id post-id]
  (let [key (str "timeline:" user-id)]
    (wcar* (car/lpush key post-id)
           (car/ltrim key 0 999))))

(defn get-timeline-ids [user-id start end]
  (wcar* (car/lrange (str "timeline:" user-id) start end)))

(defn start-pubsub-listerner! []
  (println "Starting Redis Pub/Sub listener...")
  (car/with-new-pubsub-listener (:spec server1-conn)
    {"notifications" (fn [[_ _ msg]]
                       (let [parsed-msg (json/read-str msg :key-fn keyword)
                             recipient (:recipient-id parsed-msg)]
                         (ws/send-notification-to-user! recipient parsed-msg)))}
    (car/subscribe "notifications")))

(defn publish-notification! [recipient-id actor-id post-id type]
  (let [notification-data {:recipient-id recipient-id
                           :actor-id actor-id
                           :post-id post-id
                           :type type}]
    (wcar* (car/publish "notifications" (json/write-str notification-data)))))

(defn mark-recently-posted! [user-id]
  (let [key (str "write-flag:" user-id)]
    (wcar* (car/set key "1")
           (car/expire key 3))))

(defn get-recently-posted [user-id]
  (let [key (str "write-flag:" user-id)]
    (some? (wcar* (car/get key)))))
