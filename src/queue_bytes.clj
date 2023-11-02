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
