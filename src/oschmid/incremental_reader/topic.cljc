(ns oschmid.incremental-reader.topic

  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros [oschmid.incremental-reader.topic :refer [with-reagent]]))

  (:require #?@(:clj [[clojure.string :refer [join]]
                      [datascript.core :as d]]
                :cljs [["react-dom/client" :as ReactDom]
                       ["@tiptap/core" :refer (isTextSelection)]
                       ["@tiptap/react" :refer (BubbleMenu EditorContent useEditor)]
                       ["@tiptap/starter-kit" :refer (StarterKit)]
                       [reagent.core :as r]])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [oschmid.incremental-reader.db :refer [!conn map-queue]]))

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

#?(:clj (defn topic-content [db uuid]
          (ffirst (d/q '[:find (pull ?e [:db/id :topic/content :topic/content-hash])
                         :in $ ?uuid
                         :where [?e :topic/uuid ?uuid]] db uuid))))

#?(:clj (defn check-content-hash [user-content-hash db-content-hash]
          (when-not (= user-content-hash db-content-hash)
            (throw (IllegalArgumentException. (str "Topic is out of date. db has hash=" db-content-hash " user has hash=" user-content-hash))))))

#?(:clj (defn complement-ranges [ranges length]
          (->> (flatten ranges)
               (#(if (= (first %) 0) (drop 1 %) (cons 0 %)))
               (#(if (= (last %) length) (drop-last %) (concat % [length])))
               (partition 2))))

#?(:clj (defn delete-from-topic [db uuid user-content-hash ranges]
          (let [{e :db/id content :topic/content db-content-hash :topic/content-hash} (topic-content db uuid)]
            (check-content-hash user-content-hash db-content-hash)
            (let [new-content (->> (count content)
                                   (complement-ranges ranges)
                                   (map (fn [[from to]] (subs content from to)))
                                   (apply str))]
              [{:db/id e :topic/content new-content :topic/content-hash (hash new-content)}]))))

#?(:clj (defn extract-from-topic [db userID uuid user-content-hash ranges]
          (let [{e :db/id content :topic/content db-content-hash :topic/content-hash} (topic-content db uuid)]
            (check-content-hash user-content-hash db-content-hash)
            (let [child-uuid (java.util.UUID/randomUUID)]
              [; TODO: update parent topic:
               ;   replace selected ranges with links to new child topic
               ;   create a custom node:  <topic uuid=":topic/uuid"/> (https://tiptap.dev/guide/custom-extensions#create-a-node)
               ;     render something like: <a class="topic" href="#:topic/uuid">:topic/title</a>
               ;   how to show linked :topic/title without duplicating it in parent :topic/content?
               {:topic/uuid child-uuid
                :topic/created (java.util.Date.)
                :topic/content (->> ranges (map (fn [[from to]] (subs content from to))) (join "\n\n"))
                :topic/parent e}
               (map-queue db userID (fn [q] q))])))) ; TODO: insert into queue after current topic

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
           (when-let [editor (useEditor (clj->js {:content content :editable false :extensions [StarterKit] :parseOptions {:preserveWhitespace "full"}}))]
             (when-not (= (. editor getText) content)
               ((.. editor -commands -setContent) content false (clj->js {:preserveWhitespace "full"})))
             [:<> ; TODO add 'Edit/Save' button in FloatingMenu, eventually save after each change (debounce)
              [:> EditorContent {:editor editor}]
              [:> BubbleMenu {:editor editor :shouldShow show-bubble-menu?} ; TODO replace with a bottom FloatingMenu so as not to be hidden by mobile text selection menus
               [:button {:onClick #(onEvent :delete [[0 (dec (min (.. editor -state -selection -$anchor -pos) (.. editor -state -selection -$head -pos)))]])} "Delete Before"]
               [:button {:onClick #(onEvent :delete (map range->vec (.. editor -state -selection -ranges)))} "Delete"]
               [:button {:onClick #(onEvent :extract (map range->vec (.. editor -state -selection -ranges)))} "Extract"]]])))

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

