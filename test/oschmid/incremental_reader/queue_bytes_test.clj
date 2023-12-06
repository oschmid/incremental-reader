(ns oschmid.incremental-reader.queue-bytes-test

  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [oschmid.incremental-reader.queue-bytes :as q :refer [bytes->uuid uuid->bytes]]))

;; Generate deterministic type-4 UUIDs with Clojure's test.check
;; https://gist.github.com/jcf/7a91c9473b57b1834704
(def gen-hex-char
  (gen/elements (concat (map char (range (int \a) (int \f)))
                        (range 0 9))))

(defn gen-hex-string [len]
  (gen/fmap str/join (gen/vector gen-hex-char len len)))

(def gen-uuid
  (->> (gen/tuple (gen-hex-string 8)
                  (gen/return "-")
                  (gen-hex-string 4)
                  (gen/return "-4")
                  (gen-hex-string 3)
                  (gen/return "-a")
                  (gen-hex-string 3)
                  (gen/return "-")
                  (gen-hex-string 12))
       (gen/fmap str/join)
       (gen/fmap (fn [xs] (java.util.UUID/fromString (apply str xs))))))
;; End of gist


(defn =seq [a b]
  (= (seq a) (seq b)))

(defn concat-uuids [& uuids]
  (let [bb (java.nio.ByteBuffer/wrap (byte-array (* (count uuids) q/uuid-size)))]
    (doseq [uuid uuids]
      (q/put-uuid bb uuid))
    (.array bb)))

(deftest bytes-to-and-from-uuid
  (is (thrown? NullPointerException (bytes->uuid nil)))
  (is (thrown? java.nio.BufferUnderflowException (bytes->uuid (byte-array 0))))
  (is (thrown? NullPointerException (uuid->bytes nil))))

(defspec uuid-bytes-conversion 10
  (prop/for-all [uuid gen-uuid]
    (= uuid (bytes->uuid (uuid->bytes uuid)))))

(def uuid1 (java.util.UUID/randomUUID))
(def uuid2 (java.util.UUID/randomUUID))
(def uuid3 (java.util.UUID/randomUUID))
(def uuid1-bytes (uuid->bytes uuid1))
(def uuids-12-bytes (concat-uuids uuid1 uuid2))
(def uuids-13-bytes (concat-uuids uuid1 uuid3))
(def uuids-23-bytes (concat-uuids uuid2 uuid3))
(def uuids-123-bytes (concat-uuids uuid1 uuid2 uuid3))

(deftest prepend-uuid-test
  (is (=seq uuid1-bytes (q/prepend-uuid (byte-array 0) uuid1)))
  (is (=seq uuids-123-bytes (q/prepend-uuid uuids-23-bytes uuid1))))

(deftest index-of-test
  (is (= -1 (q/index-of (byte-array 0) uuid1)))
  (is (= 0 (q/index-of uuids-123-bytes uuid1)))
  (is (= q/uuid-size (q/index-of uuids-123-bytes uuid2)))
  (is (= (* 2 q/uuid-size) (q/index-of uuids-123-bytes uuid3)))
  (is (= -1 (q/index-of uuids-123-bytes (java.util.UUID/randomUUID))))
  (is (= -1 (q/index-of nil uuid1)))
  (is (thrown? NullPointerException (q/index-of uuids-123-bytes nil))))

(deftest remove-uuid-test
  (is (thrown? Exception (q/remove-uuid (byte-array 0) uuid1)))
  (is (=seq (byte-array 0) (q/remove-uuid uuid1-bytes uuid1)))
  (is (=seq uuids-23-bytes (q/remove-uuid uuids-123-bytes uuid1)))
  (is (=seq uuids-13-bytes (q/remove-uuid uuids-123-bytes uuid2)))
  (is (=seq uuids-12-bytes (q/remove-uuid uuids-123-bytes uuid3)))
  (is (thrown? Exception (q/remove-uuid (byte-array 0) nil)))
  (is (thrown? Exception (q/remove-uuid uuid1-bytes nil)))
  (is (thrown? Exception (q/remove-uuid nil uuid1))))

(deftest move-first-uuid-to-last-test
  (is (=seq (byte-array 0) (q/move-first-uuid-to-last (byte-array 0))))
  (is (=seq uuid1-bytes (q/move-first-uuid-to-last uuid1-bytes)))
  (is (=seq (concat-uuids uuid2 uuid3 uuid1) (q/move-first-uuid-to-last uuids-123-bytes))))

