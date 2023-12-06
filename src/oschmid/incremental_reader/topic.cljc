(ns oschmid.incremental-reader.topic

  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros [oschmid.incremental-reader.topic :refer [with-reagent]]))

  (:require #?@(:clj [[datascript.core :as d]]
                :cljs [["react-dom/client" :as ReactDom]
                       ["@tiptap/core" :refer (isTextSelection)]
                       ["@tiptap/react" :refer (BubbleMenu EditorContent useEditor)]
                       ["@tiptap/starter-kit" :refer (StarterKit)]
                       [reagent.core :as r]])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [oschmid.incremental-reader.db :refer [!conn]]))

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

#?(:clj (defn map-topic-content "Update topic content" [db uuid f]
          (when-let [{e :db/id content :topic/content}
                   (ffirst (d/q '[:find (pull ?e [:db/id :topic/content])
                                  :in $ ?uuid
                                  :where [?e :topic/uuid ?uuid]] db uuid))]
            [:db/add e :topic/content (f content)])))

#?(:clj (defn complement-ranges [ranges length]
          (->> (flatten ranges)
               (#(if (= (first %) 0) (drop 1 %) (cons 0 %)))
               (#(if (= (last %) length) (drop-last %) (concat % [length])))
               (partition 2))))

#?(:clj (defn tprn [x] (prn x) x))

#?(:clj (defn delete-from-topic [db uuid ranges]
          [(map-topic-content db uuid (fn [s] (->> (count s)
                                                   (complement-ranges ranges)
                                                   (map (fn [[from to]] (subs s from to)))
                                                   (apply str))))]))

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
             [:<>
              [:> EditorContent {:editor editor}]
              [:> BubbleMenu {:editor editor :shouldShow show-bubble-menu?}
                [:button {:onClick #(onEvent :delete (map range->vec (.. editor -state -selection -ranges)))} "Delete"]]])))

#?(:cljs (defn topic-reader-wrapper [content onEvent]
           [:f> topic-reader content onEvent]))

(e/defn TopicReader [{uuid :topic/uuid content :topic/content}]
  (e/client (let [!deleteEvent (atom nil)
                  deleteEvent (e/watch !deleteEvent)]
              (when (some? deleteEvent)
                (e/server (e/discard (d/transact! !conn [[:db.fn/call delete-from-topic uuid deleteEvent]])))
                (reset! !deleteEvent nil))
              (with-reagent topic-reader-wrapper content
                (fn [eventType v]
                  (when (= eventType :delete) (reset! !deleteEvent v)))))))
; TODO add 'Extract' button 
;      create with :topic/content and :topic/parent and :topic/original IDs
;      should insert after current topic?
; TODO add 'Split' button
; TODO swipe word left to hide everything up to then, swipe right to extract? (Serves same purpose as bookmarks)
; TODO if it has :topic/source - add button to 'View Original' in a new tab (highlight topic text on page like search engines do using a fragment)

