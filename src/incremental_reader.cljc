(ns incremental-reader
  
  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros incremental-reader)) ; <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  
  (:import [hyperfiddle.electric Pending])
  (:require #?(:clj [datascript.core :as d]) ; database on server
            #?(:clj [queue-bytes :refer [bytes->uuid uuid->bytes]])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]))

; TODO fill out schema
(def schema
  {}) ; ::userid :db.type/string :db/unique
      ; ::queue :db.type/bytes <UUIDs are 16 bytes each>
      ; :extract/uuid :db.type/uuid
      ; :extract/url :db.type/string
      ; :extract/html :db.type/string <HTML formatted string>
; TODO add persistent DB
(defonce !conn #?(:clj (d/create-conn schema) :cljs nil)) ; database on server
(e/def db) ; injected database ref; Electric defs are always dynamic

; TODO this assumes db is a nested map, use datalog instead
; TODO add spec
#?(:clj (defn add-extract [id extract db] "Add a new extract to the head of the queue."
  (if (some? (get-in db [::extracts id]))
    (throw (IllegalArgumentException. "ID must be unique"))
    (-> db
      (assoc ::queue (into [id] (::queue db [])))
      (assoc-in [::extracts id] extract)))))

(e/defn URL-Import-Field [] "Extract a webpage's text and add it to the head of the queue."
  (dom/div
    (let [!url (atom "")
          url (e/watch !url)]
      (ui/input url (e/fn [v] (reset! !url v)) (dom/props {:placeholder "Enter a URL..."}))
      (ui/button
        (e/fn [] (e/server (e/discard (swap! db (partial add-extract
                                                         (java.util.UUID/randomUUID)
                                                         {::url url})))))
        (dom/props {:disabled (empty? url)}) ; TODO check for valid url
        (dom/text "Import")))))

#?(:clj (defn queue [userID]
           (d/q '[:find ?queue :in $ ?userID
                  :where [?e ::userid ?userID]
                         [(get-else $ ?e ::queue []) ?queue]] db userID)))

#?(:clj (defn extract [uuid]
          (d/q '[:find (pull ?e [*]) :in $ ?uuid
                 :where [?e :extract/uuid ?uuid]] db uuid)))

#?(:clj (defn first-extract [userID]
          (let [q (queue userID)]
            (if (empty? q) nil (extract (bytes->uuid q))))))

(e/defn Incremental-Reader []
  (e/server
   (binding [db (e/watch !conn)]
     (e/client
      (dom/link (dom/props {:rel :stylesheet :href "/incremental-reader.css"}))
      (URL-Import-Field.)
      (let [e (e/server (first-extract "oschmid1"))] ; TODO get userID from Repl Auth
        (if (some? e)
          (dom/text (str "TODO display extract: " e))
          ; TODO if it has ::url - display iframe
          ; TODO if it has ::html - display formatted text
          ; TODO save scroll position to resume next time
          ; TODO add 'Extract' button 
          ;      create with (::html or ::text) and ::parent and ::original IDs
          ;      should insert after current extract
          ; TODO add 'Delete' button to remove unnecessary text/html
          ; TODO enable when the current extract has selected text (https://developer.mozilla.org/en-US/docs/Web/API/Document/selectionchange_event)
          ; TODO get [iframe selected text](https://stackoverflow.com/questions/1471759/how-to-get-selected-text-from-iframe-with-javascript)
          (dom/div (dom/text "Welcome!")))
        )))))
