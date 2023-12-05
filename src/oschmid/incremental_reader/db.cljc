(ns oschmid.incremental-reader.db
  (:require [datascript.core :as d]
            [hyperfiddle.electric :as e]
            [oschmid.incremental-reader.queue-bytes :as q]))

#?(:clj (def schema {::userID       {; :db/valueType :db.type/string
                                       :db/unique :db.unique/identity
                                       :db/cardinality :db.cardinality/one}
                     ::queue        {; :db/valueType :db.type/bytes
                                       :db/doc "byte array of UUIDs."
                                       :db/cardinality :db.cardinality/one}
                     :topic/uuid    {; :db/valueType :db.type/uuid
                                       :db/unique :db.unique/identity
                                       :db/cardinality :db.cardinality/one}
                     :topic/source  {; :db/valueType :db.type/uri
                                       :db/cardinality :db.cardinality/one}
                     :topic/content {; :db/valueType :db.type/string
                                       :db/doc "HTML-formatted string"
                                       :db/cardinality :db.cardinality/one}}))

; TODO add persistent DB
#?(:clj (defonce !conn (d/create-conn schema))) ; database on server
#?(:clj (e/def db (e/watch !conn)))

;;;; Topic Queries/Transactions

#?(:clj (defn topic [db uuid]
          (-> (ffirst (d/q '[:find (pull ?e [*]) :in $ ?uuid
                             :where [?e :topic/uuid ?uuid]] db uuid))
              (dissoc :db/id))))

#?(:clj (defn map-queue [db userID f]
          (let [{e :db/id q :oschmid.incremental-reader/queue}
                (ffirst (d/q '[:find (pull ?e [:db/id (:oschmid.incremental-reader/queue :default (byte-array 0))])
                               :in $ ?userID
                               :where [?e :oschmid.incremental-reader/userID ?userID]] db userID))]
            (if (some? e)
              [:db/add e :oschmid.incremental-reader/queue (f q)]
              {:oschmid.incremental-reader/userID userID :oschmid.incremental-reader/queue (f (byte-array 0))}))))

#?(:clj (defn add-topic "Add topic to the head of the user's queue" [db userID {uuid :topic/uuid :as topic}]
          [(map-queue db userID #(q/prepend-uuid % uuid))
           topic]))

