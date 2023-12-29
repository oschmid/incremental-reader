(ns oschmid.incremental-reader.editor

  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros [oschmid.incremental-reader.editor :refer [with-reagent]]))

  (:require #?@(:cljs [["react-dom/client" :as ReactDom]
                       ["@tiptap/core" :refer (Editor)]
                       ["@tiptap/extension-link" :refer (Link)]
                       ["@tiptap/react" :refer (EditorContent useEditor)]
                       ["@tiptap/starter-kit" :refer (StarterKit)]
                       [clojure.string :refer [split]]
                       [hoeck.diff.lcs :refer [vec-diff]]
                       [react :as react]
                       [reagent.core :as r]])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]))

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

;;;; UI

#?(:cljs (defn extensions []
           [StarterKit Link]))

#_{:clj-kondo/ignore [:unused-binding]}
(defn format-to-schema [s]
  #?(:cljs (. (Editor. (clj->js {:extensions (extensions) :content s})) getHTML)
     :clj (throw (UnsupportedOperationException. "format-to-schema"))))

#?(:cljs (defn view-diff [expected actual]
           (->> (vec-diff (split expected #"\n") (split actual #"\n"))
                (map-indexed (fn [i [op line]]
                               (case op
                                 nil  [:div {:key i} line]
                                 :old [:div {:key i :style {:color "red"}} (str "-" line)]
                                 :new [:div {:key i :style {:color "green"}} (str "+" line)]))))))

#?(:cljs (defn range->vec [r]
           [(dec (.. r -$from -pos)) (dec (.. r -$to -pos))]))

#?(:cljs (defn html-reader [content onSelection]
           (let [onSelectionUpdate (fn [^js/SelectionUpdateProps e]
                                     (if (or (empty? (.. js/document getSelection toString)) (nil? (. e -editor)))
                                       (onSelection [])
                                       (let [state ^js/EditorState (.. e -editor -state)]
                                         (onSelection (map range->vec (.. state -selection -ranges))))))
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
                   [:> EditorContent {:editor editor}]))))))

#?(:cljs (defn html-reader-wrapper [content onSelection]
           [:f> html-reader content onSelection]))

(e/defn HTMLReader [content !selections]
  (e/client (with-reagent #_{:clj-kondo/ignore [:unresolved-symbol]} html-reader-wrapper content #(reset! !selections %))))
