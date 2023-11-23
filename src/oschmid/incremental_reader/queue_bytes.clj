(ns oschmid.incremental-reader.queue-bytes)

;; Functions to work with a queue that's represented as a byte array of UUIDs

(def uuid-size 16)

(defn put-uuid [bb uuid]
  (-> bb
      (.putLong (.getLeastSignificantBits uuid))
      (.putLong (.getMostSignificantBits uuid))))

(defn uuid->bytes [uuid]
  (-> (byte-array uuid-size)
      (java.nio.ByteBuffer/wrap)
      (put-uuid uuid)
      (.array)))

(defn bytes->uuid [bs]
  (let [bb (java.nio.ByteBuffer/wrap bs)
        low (.getLong bb)
        high (.getLong bb)]
    (java.util.UUID. high low)))

(defn size [queue-bytes]
  (/ (count queue-bytes) uuid-size))

(defn prepend-uuid [queue-bytes uuid]
  (-> (byte-array (+ (count queue-bytes) uuid-size))
      (java.nio.ByteBuffer/wrap)
      (put-uuid uuid)
      (.put queue-bytes)
      (.array)))

; TODO spec not nil
(defn index-of [queue-bytes uuid]
  (loop [i 0]
    (cond
      (>= i (count queue-bytes)) -1
      (java.util.Arrays/equals queue-bytes i (+ i uuid-size) (uuid->bytes uuid) 0 uuid-size) i
      :else (recur (+ i uuid-size)))))

(defn remove-uuid [queue-bytes uuid]
  (let [i (index-of queue-bytes uuid)]
    (if (= i -1)
      (throw (Exception. (str "Extract '" uuid "' isn't in the queue")))
      (-> (byte-array (- (count queue-bytes) uuid-size))
          (java.nio.ByteBuffer/wrap)
          (.put queue-bytes 0 i)
          (.put queue-bytes (+ i uuid-size) (- (count queue-bytes) i uuid-size))
          (.array)))))

(defn move-first-uuid-to-last [queue-bytes]
  (if (<= (count queue-bytes) uuid-size)
    queue-bytes
    (-> (byte-array (count queue-bytes))
        (java.nio.ByteBuffer/wrap)
        (.put queue-bytes uuid-size (- (count queue-bytes) uuid-size))
        (.put queue-bytes 0 uuid-size)
        (.array))))

; TODO how to test when type hints are needed?
