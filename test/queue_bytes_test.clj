(ns queue-bytes-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [queue-bytes :refer [bytes->uuid uuid->bytes concat-byte-arrays]]))

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

(deftest concat-byte-arrays-test
  (is (= (seq (byte-array 0)) (seq (concat-byte-arrays (byte-array 0)))))
  (is (= (seq (byte-array [(byte 0x43)])) (seq (concat-byte-arrays (byte-array [(byte 0x43)])))))
  (is (= (seq (byte-array 0)) (seq (concat-byte-arrays (byte-array 0) (byte-array 0)))))
  (is (= (seq (byte-array [(byte 0x43) (byte 0x6c) (byte 0x6f)]))
         (seq (concat-byte-arrays (byte-array [(byte 0x43)])
                                  (byte-array [(byte 0x6c)])
                                  (byte-array [(byte 0x6f)])))))
  (is (thrown? java.lang.NullPointerException (concat-byte-arrays nil nil)))
  (is (thrown? java.lang.NullPointerException (concat-byte-arrays nil (byte-array [(byte 0x6c)]))))
  (is (thrown? java.lang.NullPointerException (concat-byte-arrays (byte-array [(byte 0x6c)]) nil))))
