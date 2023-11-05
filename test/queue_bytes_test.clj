(ns queue-bytes-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [queue-bytes :as q :refer [bytes->uuid uuid->bytes concat-byte-arrays]]))

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


(deftest bytes-to-and-from-uuid
  (is (thrown? java.lang.NullPointerException (bytes->uuid nil)))
  (is (thrown? java.nio.BufferUnderflowException (bytes->uuid (byte-array 0))))
  (is (thrown? java.lang.NullPointerException (uuid->bytes nil))))

(defspec uuid-bytes-conversion 10
  (prop/for-all [uuid gen-uuid]
    (= uuid (bytes->uuid (uuid->bytes uuid)))))

(defn =seq [a b]
  (= (seq a) (seq b)))

(deftest concat-byte-arrays-test
  (is (=seq (byte-array 0) (concat-byte-arrays (byte-array 0))))
  (is (=seq (byte-array [(byte 0x43)]) (concat-byte-arrays (byte-array [(byte 0x43)]))))
  (is (=seq (byte-array 0) (concat-byte-arrays (byte-array 0) (byte-array 0))))
  (is (=seq (byte-array [(byte 0x43) (byte 0x6c) (byte 0x6f)])
            (concat-byte-arrays (byte-array [(byte 0x43)])
                                (byte-array [(byte 0x6c)])
                                (byte-array [(byte 0x6f)]))))
  (is (thrown? java.lang.NullPointerException (concat-byte-arrays nil nil)))
  (is (thrown? java.lang.NullPointerException (concat-byte-arrays nil (byte-array [(byte 0x6c)]))))
  (is (thrown? java.lang.NullPointerException (concat-byte-arrays (byte-array [(byte 0x6c)]) nil))))

(def uuid1 (java.util.UUID/randomUUID))
(def uuid2 (java.util.UUID/randomUUID))
(def uuid3 (java.util.UUID/randomUUID))
(def uuid1-bytes (uuid->bytes uuid1))
(def uuid2-bytes (uuid->bytes uuid2))
(def uuid3-bytes (uuid->bytes uuid3))
(def uuids-123-bytes (concat-byte-arrays uuid1-bytes uuid2-bytes uuid3-bytes))

(deftest index-of-test
         (is (= -1 (q/index-of (byte-array 0) uuid1-bytes)))
         (is (= 0 (q/index-of uuids-123-bytes uuid1-bytes)))
         (is (= 16 (q/index-of uuids-123-bytes uuid2-bytes)))
         (is (= 32 (q/index-of uuids-123-bytes uuid3-bytes)))
         (is (= -1 (q/index-of uuids-123-bytes (uuid->bytes (java.util.UUID/randomUUID)))))
         (is (= -1 (q/index-of nil uuid1-bytes)))
         (is (thrown? java.lang.NullPointerException (q/index-of uuids-123-bytes nil))))

(deftest remove-uuid-test
         (is (=seq (byte-array 0) (q/remove-uuid (byte-array 0) uuid1)))
         (is (=seq (byte-array 0) (q/remove-uuid uuid1-bytes uuid1)))
         (is (=seq (concat-byte-arrays uuid2-bytes uuid3-bytes)
                   (q/remove-uuid uuids-123-bytes uuid1)))
         (is (=seq (concat-byte-arrays uuid1-bytes uuid3-bytes)
                   (q/remove-uuid uuids-123-bytes uuid2)))
         (is (=seq (concat-byte-arrays uuid1-bytes uuid2-bytes)
                   (q/remove-uuid uuids-123-bytes uuid3)))
         (is (thrown? java.lang.NullPointerException (q/remove-uuid (byte-array 0) nil)))
         (is (thrown? java.lang.NullPointerException (q/remove-uuid uuid1-bytes nil)))
         (is (= nil (q/remove-uuid nil uuid1))))
