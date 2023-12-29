(ns oschmid.incremental-reader.topic

  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros [oschmid.incremental-reader.topic]))

  (:require #?@(:clj [[clojure.string :refer [join]]
                      [datascript.core :as d]])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [oschmid.incremental-reader.db :refer [!conn map-queue topic]]
            [oschmid.incremental-reader.editor :refer [HTMLReader]]
            [oschmid.incremental-reader.queue-bytes :as q]))

;;;; DB transactions

#?(:clj (defn check-content-hash [user-content-hash db-content-hash]
          (when-not (= user-content-hash db-content-hash)
            (throw (IllegalArgumentException. (str "Topic is out of date. db has hash=" db-content-hash " user has hash=" user-content-hash))))))

#?(:clj (defn complement-ranges [ranges length]
          (->> (flatten ranges)
               (#(if (= (first %) 0) (drop 1 %) (cons 0 %)))
               (#(if (= (last %) length) (drop-last %) (concat % [length])))
               (partition 2))))

#?(:clj (defn delete-from-topic [db uuid user-content-hash ranges]
          (let [{e :db/id content :topic/content db-content-hash :topic/content-hash} (topic db uuid)]
            (check-content-hash user-content-hash db-content-hash)
            (let [new-content (->> (count content)
                                   (complement-ranges ranges)
                                   (map (fn [[from to]] (subs content from to)))
                                   (apply str))]
              [{:db/id e :topic/content new-content :topic/content-hash (hash new-content)}]))))

#?(:clj (defn add-cloze [db uuid user-content-hash ranges]
          ; TODO insert {{c1:: ... }} around selected text
          []))

(defn topic-link [uuid]
  (str "<a class=\"topic\" href=\"#" (str uuid) "\">[[...]]</a>"))

#?(:cljs (defn try-uuid [s]
           (when (and (some? s) (re-matches #"^#[0-9a-fA-F]{8}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{12}$" s))
             (subs s 1))))

#?(:clj (defn extract-from-topic [db userID uuid user-content-hash ranges]
          (let [{e :db/id content :topic/content db-content-hash :topic/content-hash} (topic db uuid)]
            (check-content-hash user-content-hash db-content-hash)
            (let [child-uuid (java.util.UUID/randomUUID)
                  link (topic-link child-uuid)
                  size (count content)
                  new-content (->> (complement-ranges ranges size)
                                   (#(if (zero? (ffirst %)) % (cons [0 0] %)))
                                   (#(if (= (last (last %)) (count content)) % (concat % [[size size]])))
                                   (map (fn [[from to]] (subs content from to)))
                                   (join link))
                  child-content (->> ranges (map (fn [[from to]] (subs content from to))) (join "\n\n"))]
              ; TODO handle extract containing existing link(s)
              ;   e.g. A is parent to B, X is new extract of A containing link to B. A becomes parent to X, X becomes parent to B.
              ;   Look for links in `child-content`, update the parent of those topics to point to `child-uuid`
              [{:db/id e
                :topic/content new-content
                :topic/content-hash (hash new-content)}
               {:topic/uuid child-uuid
                :topic/created (java.util.Date.)
                :topic/content child-content
                :topic/content-hash (hash child-content)
                :topic/parent e}
               (map-queue db userID #(q/insert-uuid % child-uuid 1))]))))

;;;; UI

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

(e/defn TopicReader [userID {uuid :topic/uuid content :topic/content content-hash :topic/content-hash}]
  (e/client
   (let [!selections (atom [])
         selections (e/watch !selections)]
     (dom/div
      (HTMLReader. content !selections)
      (dom/div
       (Button. "Delete Before" (empty? selections) (e/fn [] (e/server (e/discard (d/transact! !conn [[:db.fn/call delete-from-topic uuid content-hash [[0 (dec (apply min (map last selections)))]]]])))))
       (Button. "Delete" (empty? selections) (e/fn [] (e/server (e/discard (d/transact! !conn [[:db.fn/call delete-from-topic uuid content-hash selections]])))))
       (Button. "Extract" (empty? selections) (e/fn [] (e/server (e/discard (d/transact! !conn [[:db.fn/call extract-from-topic userID uuid content-hash selections]])))))
       (Button. "Cloze" (empty? selections) (e/fn [] (e/server (e/discard (d/transact! !conn [[:db.fn/call add-cloze uuid content-hash selections]]))))))))))
