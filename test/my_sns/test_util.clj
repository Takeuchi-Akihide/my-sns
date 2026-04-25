(ns my-sns.test-util
  (:require [my-sns.schema :as schema]))

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
