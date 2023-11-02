(ns incremental-reader-test
  (:require [clojure.test :refer [deftest is testing]]
            [datascript.core :as d]
            [incremental-reader :as ir]
            [queue-bytes :refer [uuid->bytes concat-byte-arrays]]))

(def !empty-conn (d/create-conn ir/schema))

(def !conn (d/create-conn ir/schema))
(def uuid1 (java.util.UUID/randomUUID))
(def uuid2 (java.util.UUID/randomUUID))
(d/transact! !conn [[:db.fn/call ir/add-extract "testUserID"
                     {:extract/uuid uuid1 :extract/source "https://one.com"}]])
(d/transact! !conn [[:db.fn/call ir/add-extract "testUserID"
                     {:extract/uuid uuid2 :extract/source "https://two.com"}]])

(deftest queries
  (testing "queue"
    (is (= (seq (byte-array 0)) (seq (ir/queue @!empty-conn "unknownUserID"))))
    (is (= (seq (concat-byte-arrays (uuid->bytes uuid2) (uuid->bytes uuid1)))
           (seq (ir/queue @!conn "testUserID")))))
  (testing "extract"
    (is (= nil (ir/extract @!empty-conn (java.util.UUID/randomUUID))))
    (is (= {:db/id 3 :extract/uuid uuid2 :extract/source "https://two.com"}
           (ir/extract @!conn uuid2))))
  (testing "first-extract"
    (is (= nil (ir/first-extract @!empty-conn "unknownUserID")))
    (is (= {:db/id 3 :extract/uuid uuid2 :extract/source "https://two.com"}
           (ir/first-extract @!conn "testUserID")))))
