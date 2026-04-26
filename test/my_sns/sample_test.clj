(ns my-sns.sample-test
  (:require [clojure.test :refer :all]
            [my-sns.db :as db]
            [my-sns.sample :as sample]
            [my-sns.test-util :as test-util]))

(use-fixtures :each test-util/with-test-db)

(deftest test-load-sample-data
  (testing "サンプルデータをデータベースにロードできる"
    (sample/load-sample-data)
    (is (= 7 (count (db/query ["SELECT * FROM users"]))))
    (is (= 21 (count (db/query ["SELECT * FROM posts"]))))
    (is (= 18 (count (db/query ["SELECT * FROM follows"]))))))
