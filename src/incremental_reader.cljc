(ns incremental-reader
  
  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros incremental-reader)) ; <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  
  (:import [hyperfiddle.electric Pending])
  (:require #?(:clj [datascript.core :as d]) ; database on server
            #?(:clj [queue-bytes :refer [bytes->uuid uuid->bytes concat-byte-arrays]])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]))

#?(:clj (def schema {; TODO fix "Bad attribute specification, expected one of :db.type/tuple :db.type/ref"
                     ;; ::userID         {:db/valueType :db.type/string
                     ;;                   :db/unique :db.unique/identity
                     ;;                   :db/cardinality :db.cardinality/one}
                     ;; ::queue          {:db/doc "byte array of UUIDs. Each UUID is 16 bytes."
                     ;;                   :db/valueType :db.type/bytes
                     ;;                   :db/cardinality :db.cardinality/one}
                     ;; :extract/uuid    {:db/valueType :db.type/uuid
                     ;;                   :db/unique :db.unique/identity
                     ;;                   :db/cardinality :db.cardinality/one}
                     ;; :extract/source  {:db/valueType :db.type/string ; TODO db.type/uri?
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

; TODO return all attributes except :db/id?
#?(:clj (defn extract [db uuid]
          (ffirst (d/q '[:find (pull ?e [*]) :in $ ?uuid
                         :where [?e :extract/uuid ?uuid]] db uuid))))

#?(:clj (defn first-extract [db userID]
          (let [q (queue db userID)]
            (if (empty? q) nil (extract db (bytes->uuid q))))))

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
#?(:clj (defn add-extract [db userID extract] "Add extract to the head of the user's queue"
          [(map-queue db userID #(concat-byte-arrays (uuid->bytes (:extract/uuid extract)) %))
           extract]))

; TODO
;; #?(:clj (defn delete-extract [db userID uuid] "Delete extract at the head of a user's queue"
;;           (let [firstUUID (:extract/uuid (first-extract db userID))]
;;             (if-not (.equals firstUUID uuid)
;;               (raise uuid " is not the first extract in the queue (actual is " firstUUID)
;;               (let [e (ffirst (d/q '[:find ?e :in $ ?userID
;;                                      :where [?e ::userID ?userID]] db userID))]
;;                 [(map-queue db userID (partial drop-bytes 16))
;;                  [:db.fn/retractEntity e]]))))

(e/defn URL-Import-Field [userID] "Add a URL to the head of the user's queue."
  (dom/div
   (dom/input
    (dom/props {:placeholder "Import a URL..."})
    (dom/on "keydown"
            (e/fn [e]
                  (when (= "Enter" (.-key e))
                    (when-some [v (empty->nil (.. e -target -value))]
                      (dom/style {:background-color "yellow"}) ; loading
                      (e/server
                       (e/discard
                        (d/transact! !conn [[:db.fn/call add-extract userID
                                             {:extract/uuid (java.util.UUID/randomUUID) :extract/source v}]])
                      (set! (.-value dom/node) ""))))))))))

(e/defn Incremental-Reader []
        (e/client
          (dom/link (dom/props {:rel :stylesheet :href "/incremental-reader.css"}))
          (let [userID "oschmid1"] ; TODO get user ID from Repl Auth
            (URL-Import-Field. userID)
            (let [e (e/server (first-extract db userID))]
              (if (some? e)
                (dom/div (dom/text (str "TODO display extract: " e)))
                ; TODO add 'Delete Extract' button to remove extract
                ; TODO if it has :extract/content - display formatted text
                ; TODO if it has :extract/source - add button to 'View Original' in a new tab (highlight extract text)
                ; TODO save scroll position to resume next time
                ; TODO add 'Read Last' button
                ; TODO add 'Read Soon' button
                ; TODO add 'Extract' button 
                ;      create with :extract/content and :extract/parent and :extract/original IDs
                ;      should insert after current extract
                ; TODO add 'Delete Text' button to remove unnecessary text/html
                ; TODO enable when the current extract has selected text (https://developer.mozilla.org/en-US/docs/Web/API/Document/selectionchange_event)
                ; TODO get [iframe selected text](https://stackoverflow.com/questions/1471759/how-to-get-selected-text-from-iframe-with-javascript)
                (dom/div (dom/text "Welcome!")))
              ))))
