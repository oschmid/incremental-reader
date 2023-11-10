(ns incremental-reader
  
  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros incremental-reader)) ; <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  
  (:import [hyperfiddle.electric Pending])
  (:require #?(:clj [datascript.core :as d]) ; database on server
            #?(:clj [queue-bytes :as q])
            #?(:clj [extract-html :as html])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]))

#?(:clj (def schema {; TODO fix "Bad attribute specification, expected one of :db.type/tuple :db.type/ref"
                     ;; ::userID         {:db/valueType :db.type/string
                     ;;                   :db/unique :db.unique/identity
                     ;;                   :db/cardinality :db.cardinality/one}
                     ;; ::queue          {:db/doc "byte array of UUIDs."
                     ;;                   :db/valueType :db.type/bytes
                     ;;                   :db/cardinality :db.cardinality/one}
                     ;; :extract/uuid    {:db/valueType :db.type/uuid
                     ;;                   :db/unique :db.unique/identity
                     ;;                   :db/cardinality :db.cardinality/one}
                     ;; :extract/source  {:db/valueType :db.type/uri
                     ;;                   :db/cardinality :db.cardinality/one}
                     ;; :extract/content {:db/doc "HTML-formatted string"
                     ;;                   :db/valueType :db.type/string
                     ;;                   :db/cardinality :db.cardinality/one}
                     }))
; TODO add persistent DB
#?(:clj (defonce !conn (d/create-conn schema))) ; database on server
#?(:clj (e/def db (e/watch !conn)))

#?(:clj (defn queue [db userID]
           (-> (d/q '[:find ?queue :in $ ?userID
                      :where [?e ::userID ?userID]
                             [(get-else $ ?e ::queue (byte-array 0)) ?queue]] db userID)
               (ffirst)
               (or (byte-array 0)))))

#?(:clj (defn extract [db uuid]
          (-> (ffirst (d/q '[:find (pull ?e [*]) :in $ ?uuid
                             :where [?e :extract/uuid ?uuid]] db uuid))
              (dissoc :db/id))))

#?(:clj (defn first-extract "First extract and queue size" [db userID]
          (let [q (queue db userID)]
            [(if (empty? q) nil (extract db (q/bytes->uuid q)))
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

; TODO spec :extract/uuid is required
#?(:clj (defn add-extract "Add extract to the head of the user's queue" [db userID extract]
          [(map-queue db userID #(q/prepend-uuid % (:extract/uuid extract)))
           extract]))

#?(:clj (defn delete-extract "Delete extract from a user's queue" [db userID uuid]
          (if-let [e (ffirst (d/q '[:find ?e :in $ ?uuid
                                    :where [?e :extract/uuid ?uuid]] db uuid))]
            [(map-queue db userID #(q/remove-uuid % uuid))
             [:db.fn/retractEntity e]]
            [])))

#?(:clj (defn read-last "Move extract from first to last in a user's queue" [db userID]
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
                      (dom/style {:background-color "yellow"}) ; loading
                      (e/server
                       (e/discard
                        (let [source (html/uri v)
                              extract (if (some? source)
                                        {:extract/uuid (java.util.UUID/randomUUID)
                                         :extract/content (html/scrape v)
                                         :extract/source source}
                                        {:extract/uuid (java.util.UUID/randomUUID)
                                         :extract/content v})]
                          (d/transact! !conn [[:db.fn/call add-extract userID extract]]))))
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
          (let [userID "oschmid1"] ; TODO get user ID from Repl Auth
            (Import-Field. userID)
            (let [[e qsize] (e/server (first-extract db userID))]
              (if (some? e)
                (dom/div (dom/text (str "TODO display extract: " e))
                         (dom/div
                          (ui/button (e/fn [] (e/server (e/discard (d/transact! !conn [[:db.fn/call delete-extract userID (:extract/uuid e)]])))) (dom/text "Delete"))
                          ; TODO add delete confirmation popup
                          (Read-Last-Button. userID qsize)))
                ; TODO_ if it has :extract/content - display formatted text
                ; TODO if it has :extract/source - add button to 'View Original' in a new tab (highlight extract text)
                ; TODO add 'Read Soon' button
                ; TODO add 'Extract' button 
                ;      create with :extract/content and :extract/parent and :extract/original IDs
                ;      should insert after current extract
                ; TODO add 'Delete Text' button to remove unnecessary text/html
                ;      enable when the current extract has selected text (https://developer.mozilla.org/en-US/docs/Web/API/Document/selectionchange_event)
                ; TODO get [iframe selected text](https://stackoverflow.com/questions/1471759/how-to-get-selected-text-from-iframe-with-javascript)
                ; TODO edit extract as rich text?
                ; TODO swipe word left to hide everything up to then, swipe right to extract? (Serves same purpose as bookmarks)
                ; TODO button to "Randomize" queue
                ; TODO allow filtering of queue by tags?
                (dom/div (dom/text "Welcome!")))))))
