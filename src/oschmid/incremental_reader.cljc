(ns oschmid.incremental-reader
  
  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros oschmid.incremental-reader))
  
  (:import [hyperfiddle.electric Pending])

  (:require #?(:clj [datascript.core :as d]) ; database on server
            #?(:clj [oschmid.incremental-reader.import-html :as html])
            #?(:clj [oschmid.incremental-reader.queue-bytes :as q])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [oschmid.incremental-reader.topic :refer [TopicReader]]))

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

#?(:clj (defn queue [db userID]
           (-> (d/q '[:find ?queue :in $ ?userID
                      :where [?e ::userID ?userID]
                             [(get-else $ ?e ::queue (byte-array 0)) ?queue]] db userID)
               (ffirst)
               (or (byte-array 0)))))

#?(:clj (defn topic [db uuid]
          (-> (ffirst (d/q '[:find (pull ?e [*]) :in $ ?uuid
                             :where [?e :topic/uuid ?uuid]] db uuid))
              (dissoc :db/id))))

#?(:clj (defn first-topic "First topic and queue size" [db userID]
          (let [q (queue db userID)]
            [(if (empty? q) nil (topic db (q/bytes->uuid q)))
             (q/size q)])))

(defn empty->nil [s]
  (if (= s "") nil s))

#?(:clj (defn map-queue [db userID f]
          (let [{e :db/id q ::queue}
                (ffirst (d/q '[:find (pull ?e [:db/id (::queue :default (byte-array 0))])
                               :in $ ?userID
                               :where [?e ::userID ?userID]] db userID))]
            (if (some? e)
              [:db/add e ::queue (f q)]
              {::userID userID ::queue (f (byte-array 0))}))))

#?(:clj (defn add-topic "Add topic to the head of the user's queue" [db userID {uuid :topic/uuid :as topic}]
          [(map-queue db userID #(q/prepend-uuid % uuid))
           topic]))

#?(:clj (defn delete-topic "Delete topic from a user's queue" [db userID uuid]
          (if-let [e (ffirst (d/q '[:find ?e :in $ ?uuid
                                    :where [?e :topic/uuid ?uuid]] db uuid))]
            [(map-queue db userID #(q/remove-uuid % uuid))
             [:db.fn/retractEntity e]]
            [])))

#?(:clj (defn read-last "Move topic from first to last in a user's queue" [db userID]
          [(map-queue db userID q/move-first-uuid-to-last)]))

(e/defn Import-Field "Add HTML/text (or scrape a URL) to the head of the user's queue." [userID]
  (dom/div
   ; TODO add button to paste from clipboard? Eventually share to PWA?
   (dom/input
    (dom/props {:placeholder "Import a URL..."})
    (dom/on "keydown"
            (e/fn [e]
                  (when (= "Enter" (.-key e))
                    (when-some [v (empty->nil (.. e -target -value))]
                      (dom/style {:background-color "#e5e7e9" :disabled true})
                      (e/server
                       (e/discard
                        (let [source (html/uri v)
                              topic (if (some? source)
                                        {:topic/uuid (java.util.UUID/randomUUID)
                                         :topic/content (html/scrape v)
                                         :topic/source source}
                                        {:topic/uuid (java.util.UUID/randomUUID)
                                         :topic/content v})]
                          (d/transact! !conn [[:db.fn/call add-topic userID topic]]))))
                      (set! (.-value dom/node) ""))))))))

(e/defn Read-Last-Button [userID qsize]
        (dom/button
           (let [[state# v#] (e/do-event-pending [e# (e/listen> dom/node "click")]
                               (new (e/fn [] (e/server (e/discard (d/transact! !conn [[:db.fn/call read-last userID]]))))))
                 busy# (or (= ::e/pending state#) ; backpressure the user
                           (<= qsize 1))]
             (dom/props {:disabled busy#, :aria-busy busy#})
             (dom/text "Read Last")
             (case state# ; 4 colors
               (::e/pending ::e/failed) (throw v#)
               (::e/init ::e/ok) v#))))

(e/defn Incremental-Reader []
        (e/client
          (dom/link (dom/props {:rel :stylesheet :href "/incremental-reader.css"}))
          (let [userID "oschmid1" ; TODO get user ID from Repl Auth
                [topic qsize] (e/server (first-topic db userID))]
            (Import-Field. userID)
            (if (some? topic)
              (dom/div (TopicReader. topic)
                       (dom/div
                        (ui/button (e/fn [] (e/server (e/discard (d/transact! !conn [[:db.fn/call delete-topic userID (:topic/uuid topic)]])))) (dom/text "Delete"))
                        ; TODO add delete confirmation popup
                        (Read-Last-Button. userID qsize)))
              ; TODO add 'Read Soon' button
              ; TODO button to "Randomize" queue
              ; TODO allow filtering of queue by tags?
              (dom/div (dom/text "Welcome!"))))))

