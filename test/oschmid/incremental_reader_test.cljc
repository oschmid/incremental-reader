(ns oschmid.incremental-reader-test

  (:require [clojure.test :refer [deftest is testing]]
            [datascript.core :as d]
            [oschmid.incremental-reader :as ir]
            [oschmid.incremental-reader.db :as db]
            [oschmid.incremental-reader.queue-bytes-test :refer [=seq concat-uuids]]))

(def !empty-conn (d/create-conn db/schema))

(def !conn (d/create-conn db/schema))
(def uuid1 (java.util.UUID/randomUUID))
(def uuid2 (java.util.UUID/randomUUID))
(d/transact! !conn [[:db.fn/call db/add-topic "testUserID"
                     {:topic/uuid uuid1 :topic/source "https://one.com"}]])
(d/transact! !conn [[:db.fn/call db/add-topic "testUserID"
                     {:topic/uuid uuid2 :topic/source "https://two.com"}]])

(deftest queries
  (testing "queue" ; TODO move to db_test
    (is (=seq (byte-array 0) (db/queue @!empty-conn "unknownUserID")))
    (is (=seq (concat-uuids uuid2 uuid1) (db/queue @!conn "testUserID"))))
  (testing "topic"
    (is (= nil (db/topic @!empty-conn (java.util.UUID/randomUUID))))
    (is (= {:topic/uuid uuid2 :topic/content-hash 0 :topic/source "https://two.com"}
           (dissoc (db/topic @!conn uuid2) :db/id))))
  (testing "first-topic"
    (is (= [nil 0] (ir/first-topic @!empty-conn "unknownUserID")))
    (is (= [{:topic/uuid uuid2 :topic/content-hash 0 :topic/source "https://two.com"} 2]
           (let [[topic n] (ir/first-topic @!conn "testUserID")]
             [(dissoc topic :db/id) n])))))

(def !deletesConn (d/create-conn db/schema))
(def uuidDeleted (java.util.UUID/randomUUID))
(d/transact! !deletesConn [[:db.fn/call db/add-topic "testUserID"
                            {:topic/uuid uuid1 :topic/source "https://one.com"}]])
(d/transact! !deletesConn [[:db.fn/call db/add-topic "testUserID"
                            {:topic/uuid uuid2 :topic/source "https://two.com"}]])
(d/transact! !deletesConn [[:db.fn/call db/add-topic "testUserID"
                            {:topic/uuid uuidDeleted :topic/source "https://three.com"}]])
(d/transact! !deletesConn [[:db.fn/call ir/delete-topic "testUserID" uuidDeleted]])
(d/transact! !deletesConn [[:db.fn/call ir/delete-topic "testUserID" (java.util.UUID/randomUUID)]])

(deftest delete-topic
  (is (thrown? Exception (d/transact! !deletesConn [[:db.fn/call ir/delete-topic "unknownUserID" uuid1]])))
  (is (=seq (concat-uuids uuid2 uuid1) (db/queue @!deletesConn "testUserID")))
  (is (= nil (db/topic @!deletesConn uuidDeleted)))
  (is (= {:topic/uuid uuid1 :topic/content-hash 0 :topic/source "https://one.com"}
         (dissoc (db/topic @!deletesConn uuid1) :db/id)))
  (is (= {:topic/uuid uuid2 :topic/content-hash 0 :topic/source "https://two.com"}
         (dissoc (db/topic @!deletesConn uuid2) :db/id))))

