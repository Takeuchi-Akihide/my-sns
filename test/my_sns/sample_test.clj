(ns my-sns.sample-test
  (:require [clojure.test :refer :all]
            [my-sns.sample :as sample]
            [my-sns.test-util :as test-util]))

(use-fixtures :each test-util/with-test-env)

(deftest test-load-sample-data
  (testing "サンプルデータをデータベースにロードできる"
    (sample/load-sample-data)
    (is (= 7 (count (test-util/query ["SELECT * FROM users"]))))
    (is (= 18 (count (test-util/query ["SELECT * FROM follows"]))))))
