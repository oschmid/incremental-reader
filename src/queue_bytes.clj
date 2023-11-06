(ns queue-bytes)

;; Functions to work with a queue that's represented as a byte array of UUIDs
;; 
;; UUIDs are 16 bytes

(defn put-uuid [bb uuid]
  (-> bb
      (.putLong (.getLeastSignificantBits uuid))
      (.putLong (.getMostSignificantBits uuid))))

(defn uuid->bytes [uuid]
  (-> (byte-array 16)
      (java.nio.ByteBuffer/wrap)
      (put-uuid uuid)
      (.array)))

(defn bytes->uuid [bs]
  (let [bb (java.nio.ByteBuffer/wrap bs)
        low (.getLong bb)
        high (.getLong bb)]
    (java.util.UUID. high low)))

(defn prepend-uuid [queue-bytes uuid]
  (-> (byte-array (+ (count queue-bytes) 16))
      (java.nio.ByteBuffer/wrap)
      (put-uuid uuid)
      (.put queue-bytes)
      (.array)))

; TODO spec not nil
(defn index-of [queue-bytes uuid]
  (loop [i 0]
    (cond
      (>= i (count queue-bytes)) -1
      (java.util.Arrays/equals queue-bytes i (+ i 16) (uuid->bytes uuid) 0 16) i ; TODO optimize by comparing from last to first? First 6 or 7 bytes of each UUID will be the same...
      :else (recur (+ i 16)))))

(defn remove-uuid [queue-bytes uuid]
  (let [i (index-of queue-bytes uuid)]
    (if (= i -1)
      (throw (Exception. (str "Extract '" uuid "' isn't in the queue")))
      (-> (byte-array (- (count queue-bytes) 16))
        (java.nio.ByteBuffer/wrap)
        (.put queue-bytes 0 i)
        (.put queue-bytes (+ i 16) (- (count queue-bytes) i 16))
        (.array)))))

; TODO how to test when type hints are needed?
