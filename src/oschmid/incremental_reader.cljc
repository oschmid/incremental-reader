(ns oschmid.incremental-reader

  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros oschmid.incremental-reader))

  (:require #?@(:clj [[datascript.core :as d] ; database on server
                      [oschmid.incremental-reader.import-html :as html]
                      [oschmid.incremental-reader.queue-bytes :as q]])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [oschmid.incremental-reader.anki :as anki]
            [oschmid.incremental-reader.db :refer [!conn add-topic db map-queue topic queue]]
            [oschmid.incremental-reader.topic :refer [TopicReader format-to-schema]]))

#?(:clj (defn first-topic "First topic and queue size" [db userID]
          (let [q (queue db userID)]
            [(if (empty? q) nil (topic db (q/bytes->uuid q)))
             (q/size q)])))

(defn empty->nil [s]
  (if (= s "") nil s))

#?(:clj (defn delete-topic "Delete topic from a user's queue" [db userID uuid]
          (if-let [e (ffirst (d/q '[:find ?e :in $ ?uuid
                                    :where [?e :topic/uuid ?uuid]] db uuid))]
            [(map-queue db userID #(q/remove-uuid % uuid))
             [:db.fn/retractEntity e]]
            [])))

#?(:clj (defn read-last "Move topic from first to last in a user's queue" [db userID]
          [(map-queue db userID q/move-first-uuid-to-last)]))

; TODO make a button show this as a popup
(e/defn Import-Field "Add HTML/text (or scrape a URL) to the head of the user's queue." [userID]
  (dom/div
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
                          scraped (if (some? source) (html/scrape v) (html/clean v))
                          formatted (e/client (format-to-schema scraped))
                          ; TODO don't trust clientside text: content (html/clean formatted (if (some? source) v "localhost"))
                          ; TODO import page title, use first 50 chars if pasted
                          topic {:topic/content formatted
                                 :topic/created (java.util.Date.)
                                 :topic/uuid (java.util.UUID/randomUUID)}]
                      (d/transact! !conn [[:db.fn/call add-topic userID (if (some? source) (assoc topic :topic/source source) topic)]]))))
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

#?(:cljs (defn count-clozes [s]
           (count (re-seq #"\{\{c\d+::" s))))

(e/defn Button [label disabled on-click]
  (dom/button
   (let [[state# v#] (e/do-event-pending [e# (e/listen> dom/node "click")]
                                         (new on-click))
         busy# (or (= ::e/pending state#) ; backpressure the user
                   disabled)]
     (dom/props {:disabled busy#, :aria-busy busy#})
     (dom/text label)
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
       (dom/div (TopicReader. userID topic)
                (dom/div
                 (ui/button (e/fn [] (e/server (e/discard (d/transact! !conn [[:db.fn/call delete-topic userID (:topic/uuid topic)]])))) (dom/text "Delete Topic"))
                        ; TODO add delete confirmation popup, or Undo functionality for whole app
                 (Read-Last-Button. userID qsize)
                 (let [clozes (count-clozes (:topic/content topic))]
                   (Button. (if (= clozes 1) "Sync Cloze" "Sync Clozes")
                            (> clozes 0)
                            (e/fn [] (e/server (e/discard (anki/add-cloze userID "Default" (:topic/content topic)))))))))
              ; TODO add 'Read Soon' button
              ; TODO button to "Randomize" queue
       (dom/div (dom/text "Welcome!"))))))

