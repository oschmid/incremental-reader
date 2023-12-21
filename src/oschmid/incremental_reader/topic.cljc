(ns oschmid.incremental-reader.topic

  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros [oschmid.incremental-reader.topic :refer [with-reagent]]))

  (:require #?@(:clj [[clojure.string :refer [join]]
                      [datascript.core :as d]]
                :cljs [["react-dom/client" :as ReactDom]
                       ["@tiptap/core" :refer (Editor)]
                       ["@tiptap/extension-link" :refer (Link)]
                       ["@tiptap/react" :refer (EditorContent useEditor)]
                       ["@tiptap/starter-kit" :refer (StarterKit)]
                       [clojure.string :refer [split]]
                       [hoeck.diff.lcs :refer [vec-diff]]
                       [react :as react]
                       [reagent.core :as r]])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [oschmid.incremental-reader.db :refer [!conn map-queue topic]]
            [oschmid.incremental-reader.queue-bytes :as q]))

;;;; Reagent Interop

#?(:cljs (def ReactRootWrapper
           (r/create-class
            {:render (fn [this]
                       (let [[_ Component & args] (r/argv this)]
                         (into [Component] args)))})))

#?(:cljs (defn create-root
           "See https://reactjs.org/docs/react-dom-client.html#createroot"
           ([node] (create-root node (str (gensym))))
           ([node id-prefix] (ReactDom/createRoot node #js {:identifierPrefix id-prefix}))))

#?(:cljs (defn render [root & args]
           (.render root (r/as-element (into [ReactRootWrapper] args)))))

(defmacro with-reagent [& args]
  `(dom/div  ; React will hijack this element and empty it.
    (let [root# (create-root dom/node)]
      (render root# ~@args)
      (e/on-unmount #(.unmount root#)))))

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

#?(:cljs (defn extensions []
           [StarterKit Link]))

#?(:cljs (defn format-to-schema [s]
           ; TODO use getHTMLFromFragment instead of Editor? https://github.com/ueberdosis/tiptap/blob/1378883e9ed27934816debcdc10c170d9a74cbc2/packages/core/src/Editor.ts#L428
           (. (Editor. (clj->js {:extensions (extensions) :content s})) getHTML))
   :clj (defn format-to-schema [s]
          (throw (UnsupportedOperationException. "format-to-schema"))))

#?(:cljs (defn view-diff [expected actual]
           (->> (vec-diff (split expected #"\n") (split actual #"\n"))
                (map-indexed (fn [i [op line]]
                               (case op
                                 nil  [:div {:key i} line]
                                 :old [:div {:key i :style {:color "red"}} (str "-" line)]
                                 :new [:div {:key i :style {:color "green"}} (str "+" line)]))))))

#?(:cljs (defn range->vec [r]
           [(dec (.. r -$from -pos)) (dec (.. r -$to -pos))]))

#?(:cljs (defn topic-reader [content onEvent]
           (let [[selection set-selection] (react/useState "")
                 onSelectionUpdate (fn [^js/SelectionUpdateProps e]
                                     (if (empty? (or (.. js/document getSelection toString) e))
                                       (set-selection "")
                                       (let [state ^js/EditorState (.. e -editor -state)]
                                         (set-selection ^String (. (. state -doc) textBetween (.. state -selection -from) (.. state -selection -to))))))
                 [expected-content set-expected-content] (react/useState (fn [] (set! (. js/document -onselectionchange) onSelectionUpdate)))
                 editor (useEditor (clj->js {:content content
                                             :editable false
                                             :extensions (extensions)
                                             :parseOptions {:preserveWhitespace "full"}
                                             :onSelectionUpdate onSelectionUpdate}))]
             (when (some? editor)
               (if-not (= content expected-content)
                 (do
                   ((.. editor -commands -setContent) content false (clj->js {:preserveWhitespace "full"}))
                   (set-expected-content content))
                 (if-not (= (. editor getHTML) expected-content)
                   [:div
                    [:p "Tiptap schema blocked the following content:"]
                    [:<>
                     (view-diff expected-content (. editor getHTML))]]
                   [:div ; TODO add 'Edit/Save' button in FloatingMenu, eventually save after each change (debounce)
                    [:> EditorContent {:editor editor}]
                    [:div
                     [:button {:disabled (empty? selection)
                               :onClick #(onEvent :delete [[0 (dec (min (.. editor -state -selection -$anchor -pos) (.. editor -state -selection -$head -pos)))]])} "Delete Before"]
                     [:button {:disabled (empty? selection)
                               :onClick #(onEvent :delete (map range->vec (.. editor -state -selection -ranges)))} "Delete"]
                     [:button {:disabled (empty? selection)
                               :onClick #(onEvent :extract (map range->vec (.. editor -state -selection -ranges)))} "Extract"]]]))))))

#?(:cljs (defn topic-reader-wrapper [content onEvent]
           [:f> topic-reader content onEvent]))

(e/defn TopicReader [userID {uuid :topic/uuid content :topic/content content-hash :topic/content-hash}]
  (e/client
   (let [!event (atom nil)]
     (when-let [[eventType v] (e/watch !event)]
       (case eventType
         :delete (e/server (e/discard (d/transact! !conn [[:db.fn/call delete-from-topic uuid content-hash v]])))
         :extract (e/server (e/discard (d/transact! !conn [[:db.fn/call extract-from-topic userID uuid content-hash v]]))))
       (reset! !event nil))
     (with-reagent topic-reader-wrapper content #(reset! !event [%1 %2])))))
; TODO add create question button
;      copy selected text as question, cloze, or answer
; TODO add 'Split' button
; TODO swipe word left to hide everything up to then, swipe right to extract? (Serves same purpose as bookmarks)
; TODO if it has :topic/source - add button to 'View Original' in a new tab (highlight topic text on page like search engines do using a fragment)

