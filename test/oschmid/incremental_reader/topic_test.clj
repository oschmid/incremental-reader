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
(d/transact! !conn [[:db.fn/call topic/delete-from-topic uuid1 (hash "0123456789") [[0 5]]]])
(d/transact! !conn [[:db.fn/call topic/delete-from-topic uuid2 (hash "0123456789") [[1 2] [5 9]]]])

(deftest delete-from-topic-test
  (is (= "56789" (:topic/content (db/topic @!conn uuid1))))
  (is (= "02349" (:topic/content (db/topic @!conn uuid2))))
  (is (thrown? IllegalArgumentException (d/transact! !conn [[:db.fn/call topic/delete-from-topic uuid1 (hash "bad") [[0 5]]]]))))

(defn second-uuid [queue-bytes]
  (-> (byte-array q/uuid-size)
      (java.nio.ByteBuffer/wrap)
      (.put queue-bytes q/uuid-size q/uuid-size)
      (.array)
      (q/bytes->uuid)))

(deftest extract-from-topic-test
  (testing "extract from middle"
    (let [uuid3 (java.util.UUID/randomUUID)
          _ (d/transact! !conn [[:db.fn/call db/add-topic "testUserID3" {:topic/uuid uuid3 :topic/content "before extract after"}]])
          _ (d/transact! !conn [[:db.fn/call topic/extract-from-topic "testUserID3" uuid3 (hash "before extract after") [[7 14]]]])
          queue (db/queue @!conn "testUserID3")
          child-uuid (second-uuid queue)
          parent-content (str "before <a class=\"topic\" data-id=\"" (.toString child-uuid) "\">[[...]]</a> after")
          parent (db/topic @!conn uuid3)]
      (is (= {:topic/uuid (q/bytes->uuid queue) :topic/content parent-content :topic/content-hash (hash parent-content)} (dissoc parent :db/id :topic/created)))
      (is (= {:topic/uuid child-uuid :topic/content "extract" :topic/content-hash (hash "extract") :topic/parent (:db/id parent)} (dissoc (db/topic @!conn child-uuid) :db/id :topic/created)))))
  (testing "extract from start"
    (let [uuid4 (java.util.UUID/randomUUID)
          _ (d/transact! !conn [[:db.fn/call db/add-topic "testUserID4" {:topic/uuid uuid4 :topic/content "extract after"}]])
          _ (d/transact! !conn [[:db.fn/call topic/extract-from-topic "testUserID4" uuid4 (hash "extract after") [[0 7]]]])
          queue (db/queue @!conn "testUserID4")
          child-uuid (second-uuid queue)
          parent-content (str "<a class=\"topic\" data-id=\"" (.toString child-uuid) "\">[[...]]</a> after")
          parent (db/topic @!conn uuid4)]
      (is (= {:topic/uuid (q/bytes->uuid queue) :topic/content parent-content :topic/content-hash (hash parent-content)} (dissoc parent :db/id :topic/created)))
      (is (= {:topic/uuid child-uuid :topic/content "extract" :topic/content-hash (hash "extract") :topic/parent (:db/id parent)} (dissoc (db/topic @!conn child-uuid) :db/id :topic/created)))))
  (testing "extract from end"
    (let [uuid5 (java.util.UUID/randomUUID)
          _ (d/transact! !conn [[:db.fn/call db/add-topic "testUserID5" {:topic/uuid uuid5 :topic/content "before extract"}]])
          _ (d/transact! !conn [[:db.fn/call topic/extract-from-topic "testUserID5" uuid5 (hash "before extract") [[7 14]]]])
          queue (db/queue @!conn "testUserID5")
          child-uuid (second-uuid queue)
          parent-content (str "before <a class=\"topic\" data-id=\"" (.toString child-uuid) "\">[[...]]</a>")
          parent (db/topic @!conn uuid5)]
      (is (= {:topic/uuid (q/bytes->uuid queue) :topic/content parent-content :topic/content-hash (hash parent-content)} (dissoc parent :db/id :topic/created)))
      (is (= {:topic/uuid child-uuid :topic/content "extract" :topic/content-hash (hash "extract") :topic/parent (:db/id parent)} (dissoc (db/topic @!conn child-uuid) :db/id :topic/created))))))

