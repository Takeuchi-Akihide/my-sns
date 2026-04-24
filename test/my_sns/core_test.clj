(ns my-sns.core-test
  (:require [clojure.test :refer :all]
            [my-sns.core :refer :all]))

;; 今後"lein run server"や"lein run recreate"の動作のテストを追加する。
;; mockサーバを作成してhandlerの動作を確認することも検討する。

(deftest test-main-entry-point-exists
  (testing "-main関数が存在する"
    (is (fn? -main))))

