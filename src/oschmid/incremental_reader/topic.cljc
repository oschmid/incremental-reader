(ns oschmid.incremental-reader.topic

  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros [oschmid.incremental-reader.topic :refer [with-reagent]]))

  (:require #?@(:clj [[clojure.string :refer [join]]
                      [datascript.core :as d]]
                :cljs [["react-dom/client" :as ReactDom]
                       ["@tiptap/core" :refer (isTextSelection)]
                       ["@tiptap/extension-link" :refer (Link)]
                       ["@tiptap/react" :refer (BubbleMenu EditorContent useEditor)]
                       ["@tiptap/starter-kit" :refer (StarterKit)]
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

#?(:clj (defn topic-link [uuid]
          (str "<a class=\"topic\" data-id=\"" (.toString uuid) "\" href=\"#\">[[...]]</a>")))

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

; Based on the default shouldShow method except it'll work on a non-editable Editor
; (see https://github.com/ueberdosis/tiptap/blob/main/packages/extension-bubble-menu/src/bubble-menu-plugin.ts#L47)
#?(:cljs (defn show-bubble-menu? [menu]
           (let [^js/Node doc (.. menu -state -doc)
                 from (. menu -from)
                 to (. menu -to)
                 selectionEmpty (.. menu -state -selection -empty)
                 isEmptyTextBlock (and (empty? (. doc textBetween from to)) (isTextSelection (.. menu -state -selection)))]
             (not (or selectionEmpty isEmptyTextBlock)))))

#?(:cljs (defn range->vec [r]
           [(dec (.. r -$from -pos)) (dec (.. r -$to -pos))]))

#?(:cljs (defn topic-reader [content onEvent]
           (let [editor (useEditor (clj->js {:content content :editable false :extensions [StarterKit Link] :parseOptions {:preserveWhitespace "full"}}))
                 [expected-content set-expected-content] (react/useState nil)]
             (when (some? editor)
               (if-not (= content expected-content)
                 (do
                   ((.. editor -commands -setContent) content false (clj->js {:preserveWhitespace "full"}))
                   (set-expected-content content))
                 (if-not (= (. editor getText) expected-content)
                   "schema has blocked content"
                   [:div ; TODO add 'Edit/Save' button in FloatingMenu, eventually save after each change (debounce)
                    [:> EditorContent {:editor editor}]
                    [:> BubbleMenu {:editor editor :shouldShow show-bubble-menu?} ; TODO replace with a bottom FloatingMenu so as not to be hidden by mobile text selection menus
                     [:button {:onClick #(onEvent :delete [[0 (dec (min (.. editor -state -selection -$anchor -pos) (.. editor -state -selection -$head -pos)))]])} "Delete Before"]
                     [:button {:onClick #(onEvent :delete (map range->vec (.. editor -state -selection -ranges)))} "Delete"]
                     [:button {:onClick #(onEvent :extract (map range->vec (.. editor -state -selection -ranges)))} "Extract"]]]))))))

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

