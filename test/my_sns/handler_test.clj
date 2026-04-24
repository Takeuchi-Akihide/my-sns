(ns my-sns.handler-test
  (:require [clojure.test :refer :all]
            [my-sns.handler :as handler]))

;; ==============================
;; ユーティリティ関数のテスト
;; ==============================

(deftest test-check-required-params
  (testing "認証IDがない場合は例外を発生させる"
    (is (thrown-with-msg? Exception #"Authentication required"
                         (handler/check-required-params nil {}))))

  (testing "必須パラメータが空白の場合は例外を発生させる"
    (is (thrown-with-msg? Exception #"Missing required parameter"
                         (handler/check-required-params "user-id" {"key" ""}))))

  (testing "必須パラメータが存在する場合は例外を発生させない"
    (is (nil? (handler/check-required-params "user-id" {"key" "value"})))))
