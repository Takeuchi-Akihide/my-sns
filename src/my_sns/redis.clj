(ns my-sns.redis
  (:require [taoensso.carmine :as car]))

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
