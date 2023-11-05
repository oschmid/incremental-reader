(ns queue-bytes)

;; Functions to work with a queue that's represented as a byte array of UUIDs
;; 
;; UUIDs are 16 bytes

(defn uuid->bytes [uuid]
  (let [bb (java.nio.ByteBuffer/wrap (byte-array 16))]
    (.putLong bb (.getMostSignificantBits uuid))
    (.putLong bb (.getLeastSignificantBits uuid))
    (.array bb)))

(defn bytes->uuid [bs]
  (let [bb (java.nio.ByteBuffer/wrap bs)
        high (.getLong bb)
        low (.getLong bb)]
    (java.util.UUID. high low)))

;; https://stackoverflow.com/a/26670298 by Antti Karanta
(defn concat-byte-arrays [& byte-arrays]
  (when (not-empty byte-arrays)
    (let [total-size (reduce + (map count byte-arrays))
          result (byte-array total-size)
          bb (java.nio.ByteBuffer/wrap result)]
      (doseq [ba byte-arrays]
        (.put bb ba))
      result)))

; TODO spec not nil
(defn index-of [queue-bytes uuid-bytes]
  (loop [i 0]
    (cond
      (>= i (count queue-bytes)) -1
      (java.util.Arrays/equals queue-bytes i (+ i 16) uuid-bytes 0 16) i ; TODO optimize by comparing from last to first? First 6 or 7 bytes of each UUID will be the same...
      :else (recur (+ i 16)))))

(defn remove-uuid [queue-bytes uuid]
  (let [i (index-of queue-bytes (uuid->bytes uuid))]
    (if (= i -1)
      queue-bytes
      (concat-byte-arrays (java.util.Arrays/copyOfRange queue-bytes 0 i)
                          (java.util.Arrays/copyOfRange queue-bytes (+ i 16) (count queue-bytes))))))
