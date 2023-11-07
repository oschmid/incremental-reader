(ns incremental-reader-test
  (:require [clojure.test :refer [deftest is testing]]
            [datascript.core :as d]
            [incremental-reader :as ir]]
            [queue-bytes-test :refer [=seq concat-uuids]]))

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
                  (is (=seq (byte-array 0) (ir/queue @!empty-conn "unknownUserID")))
                  (is (=seq (concat-uuids uuid2 uuid1) (ir/queue @!conn "testUserID"))))
         (testing "extract"
                  (is (= nil (ir/extract @!empty-conn (java.util.UUID/randomUUID))))
                  (is (= {:db/id 3 :extract/uuid uuid2 :extract/source "https://two.com"}
                         (ir/extract @!conn uuid2)))
                  ;; (is (= nil (ir/extract @!conn uuidDeleted)))
                  )
         (testing "first-extract"
                  (is (= nil (ir/first-extract @!empty-conn "unknownUserID")))
                  (is (= {:db/id 3 :extract/uuid uuid2 :extract/source "https://two.com"}
                         (ir/first-extract @!conn "testUserID")))))

(def !deletesConn (d/create-conn ir/schema))
(def uuidDeleted (java.util.UUID/randomUUID))
(d/transact! !deletesConn [[:db.fn/call ir/add-extract "testUserID"
                            {:extract/uuid uuid1 :extract/source "https://one.com"}]])
(d/transact! !deletesConn [[:db.fn/call ir/add-extract "testUserID"
                            {:extract/uuid uuid2 :extract/source "https://two.com"}]])
(d/transact! !deletesConn [[:db.fn/call ir/add-extract "testUserID"
                            {:extract/uuid uuidDeleted :extract/source "https://three.com"}]])
(d/transact! !deletesConn [[:db.fn/call ir/delete-extract "testUserID" uuidDeleted]])
(d/transact! !deletesConn [[:db.fn/call ir/delete-extract "testUserID" (java.util.UUID/randomUUID)]])

(deftest delete-extract
          (is (thrown? Exception (d/transact! !deletesConn [[:db.fn/call ir/delete-extract "unknownUserID" uuid1]])))
         (is (=seq (concat-uuids uuid2 uuid1) (ir/queue @!deletesConn "testUserID")))
         (is (= nil (ir/extract @!deletesConn uuidDeleted)))
         (is (= {:db/id 2 :extract/uuid uuid1 :extract/source "https://one.com"}
                (ir/extract @!deletesConn uuid1)))
         (is (= {:db/id 3 :extract/uuid uuid2 :extract/source "https://two.com"}
           (ir/extract @!deletesConn uuid2))))
