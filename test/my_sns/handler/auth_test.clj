(ns my-sns.handler.auth-test
  (:require [clojure.test :refer :all]
            [buddy.hashers :as hashers]
            [my-sns.handler.auth :as auth]
            [my-sns.schema :as schema]
            [my-sns.test-util :as test-util]))

;; fixtureの設定
(use-fixtures :each test-util/with-test-db)

;; ==============================
;; 認証関連のテスト
;; ==============================

(deftest test-login-handler
  (let [username "test-user"
        password "password123"
        email "test@example.com"
        display-name "Test User"
        password-hash (hashers/derive password)]

    ;; テストユーザーを作成
    (schema/add-user! username display-name email password-hash)

    (testing "正しいユーザー名とパスワードでログインできる"
      (let [result (auth/login-handler {:body {:username username :password password}})]
        (is (= 200 (:status result)))
        (is (contains? (:body result) :token))))

    (testing "間違ったパスワードでログインできない"
      (is (thrown-with-msg? Exception #"Invalid username or password"
                            (auth/login-handler {:body {:username username :password "wrong-password"}}))))

    (testing "存在しないユーザーでログインできない"
      (is (thrown-with-msg? Exception #"Invalid username or password"
                            (auth/login-handler {:body {:username "nonexistent" :password password}}))))))
