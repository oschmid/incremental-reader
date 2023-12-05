(ns oschmid.incremental-reader.topic-test

  (:require [clojure.test :refer [deftest is testing]]
            [datascript.core :as d]
            [oschmid.incremental-reader.db :as db]
            [oschmid.incremental-reader.topic :as topic]
            [oschmid.incremental-reader.queue-bytes :as q]))

(deftest complement-ranges-test
  (is (= [] (topic/complement-ranges [[0 5]] 5)))
  (is (= [[4 5]] (topic/complement-ranges [[0 4]] 5)))
  (is (= [[0 1]] (topic/complement-ranges [[1 5]] 5)))
  (is (= [[0 1] [4 5]] (topic/complement-ranges [[1 4]] 5)))
  (is (= [[2 3]] (topic/complement-ranges [[0 2] [3 5]] 5)))
  (is (= [[2 3] [4 5]] (topic/complement-ranges [[0 2] [3 4]] 5)))
  (is (= [[0 1] [2 3]] (topic/complement-ranges [[1 2] [3 5]] 5)))
  (is (= [[0 1] [2 3] [4 5]] (topic/complement-ranges [[1 2] [3 4]] 5))))

(def !conn (d/create-conn db/schema))
(def uuid1 (java.util.UUID/randomUUID))
(def uuid2 (java.util.UUID/randomUUID))
(d/transact! !conn [[:db.fn/call db/add-topic "testUserID"
                     {:topic/uuid uuid1 :topic/content "0123456789"}]])
(d/transact! !conn [[:db.fn/call db/add-topic "testUserID"
                     {:topic/uuid uuid2 :topic/content "0123456789"}]])
(d/transact! !conn [[:db.fn/call topic/delete-from-topic uuid1 [[0 5]]]])
(d/transact! !conn [[:db.fn/call topic/delete-from-topic uuid2 [[1 2] [5 9]]]])

(deftest delete-from-topic-test
  (is (= "56789" (:topic/content (db/topic @!conn uuid1))))
  (is (= "02349" (:topic/content (db/topic @!conn uuid2)))))

