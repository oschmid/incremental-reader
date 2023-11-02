(ns queue-bytes-test
  (:require [clojure.test :refer [deftest is]]
            [queue-bytes :refer [bytes->uuid uuid->bytes concat-byte-arrays]]))

(def uuid (java.util.UUID/randomUUID))

(deftest uuid-bytes-conversion
  (is (= uuid (bytes->uuid (uuid->bytes uuid)))))

(deftest concat-byte-arrays-test
  (is (= (seq (byte-array 0)) (seq (concat-byte-arrays (byte-array 0) (byte-array 0)))))
         ; TODO concat two values
         ; TODO concat nils
         ; TODO concat one nil first
          ; TODO concat one nil second
         )