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

; TODO fix (concat-byte-arrays (uuid->bytes (java.util.UUID/randomUUID)) [])
;; Execution error (IllegalArgumentException) at queue-bytes/concat-byte-arrays (queue_bytes.clj:26).
;; No matching method put found taking 1 args for class java.nio.HeapByteBuffer

;; https://stackoverflow.com/a/26670298
(defn concat-byte-arrays [& byte-arrays]
  (when (not-empty byte-arrays)
    (let [total-size (reduce + (map count byte-arrays))
          result     (byte-array total-size)
          bb         (java.nio.ByteBuffer/wrap result)]
      (doseq [ba byte-arrays]
        (.put bb ba))
      result)))
