(ns my-sns.handler-test
  (:require [clojure.test :refer :all]
            [buddy.hashers :as hashers]
            [my-sns.handler :as handler]
            [my-sns.schema :as schema]
            [my-sns.test-util :as test-util]))

;; fixtureの設定
(use-fixtures :each test-util/with-test-db)

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

(deftest test-list-timeline-handler
  (let [password-hash (hashers/derive "password")]
    (schema/add-user! "user1" "User 1" "user1@example.com" password-hash)
    (schema/add-user! "user2" "User 2" "user2@example.com" password-hash)
    (let [user1 (schema/get-user-by-username "user1")
          user2 (schema/get-user-by-username "user2")
          user1-id (:users/id user1)
          user2-id (:users/id user2)]
      (schema/create-post! user1-id nil "User1 post 1")
      (schema/create-post! user2-id nil "User2 post 1")
      (schema/follow-user! user1-id user2-id)
      (let [result (handler/list-timeline-handler {:identity {:user_id user1-id}
                                                   :params {:limit "1"}})
            body (:body result)
            data (:data body)
            meta (:meta body)]
        (is (= 200 (:status result)))
        (is (= 1 (count data)))
        (is (= true (:has_next meta)))
        (is (some? (:next_cursor_date meta)))
        (is (some? (:next_cursor_id meta)))
        (let [next-result (handler/list-timeline-handler
                           {:identity {:user_id user1-id}
                            :params {:limit "1"
                                     :cursor_date (:next_cursor_date meta)
                                     :cursor_id (:next_cursor_id meta)}})
              next-body (:body next-result)
              next-data (:data next-body)]
          (is (= 1 (count next-data))))))))
