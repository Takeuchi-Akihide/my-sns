(ns my-sns.test-util
  (:require [my-sns.schema :as schema]))

;; ==============================
;; テスト用フィクスチャー
;; ==============================

(defn setup-test-db []
  (schema/drop-schema!)
  (schema/create-schema!))

(defn teardown-test-db []
  (schema/drop-schema!))

(defn with-test-db [f]
  (setup-test-db)
  (try
    (f)
    (finally
      (teardown-test-db))))
