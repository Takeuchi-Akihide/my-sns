(ns my-sns.test-util
  (:require [clojure.core.async :as async]
            [my-sns.redis :as redis]
            [my-sns.schema :as schema]
            [my-sns.worker :as worker]))

;; ==============================
;; テスト用フィクスチャー
;; ==============================

(defn setup-test-db []
  (println "[TEST] Setting up test database (mysns_test_db)...")
  (schema/drop-schema!)
  (schema/create-schema!))

(defn teardown-test-db []
  (println "[TEST] Tearing down test database...")
  (schema/drop-schema!))

(defn with-test-db [f]
  (setup-test-db)
  (try
    (f)
    (finally
      (teardown-test-db))))

(defn with-mocked-redis [f]
  (let [timelines (atom {})
        likes (atom {})
        dirty-posts (atom #{})]
    (with-redefs [redis/push-to-timeline!
                  (fn [user-id post-id]
                    (swap! timelines update user-id (fnil #(vec (cons post-id %)) []))
                    nil)
                  redis/get-timeline-ids
                  (fn [user-id start end]
                    (let [ids (get @timelines user-id [])]
                      (->> ids
                           (drop start)
                           (take (inc (- end start)))
                           vec)))
                  redis/incr-like-count
                  (fn [post-id]
                    (swap! likes update post-id (fnil inc 0)))
                  redis/decr-like-count
                  (fn [post-id]
                    (swap! likes update post-id (fnil #(max 0 (dec %)) 0)))
                  redis/get-likes-batch
                  (fn [post-ids]
                    (mapv #(some-> (get @likes %) str) post-ids))
                  redis/mark-dirty!
                  (fn [post-id]
                    (swap! dirty-posts conj post-id))
                  redis/pop-dirty-posts!
                  (fn []
                    (let [ids (vec @dirty-posts)]
                      (reset! dirty-posts #{})
                      ids))
                  async/put!
                  (fn
                    ([ch val]
                     (worker/process-event! val)
                     true)
                    ([ch val _]
                     (worker/process-event! val)
                     true))]
      (f))))

(defn with-test-env [f]
  (with-test-db
    #(with-mocked-redis f)))
