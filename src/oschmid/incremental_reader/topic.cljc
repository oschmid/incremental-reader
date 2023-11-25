(ns oschmid.incremental-reader.topic

  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros [oschmid.incremental-reader.topic :refer [with-reagent]]))

  (:require #?(:cljs [reagent.core :as r])
            #?(:cljs ["react-dom/client" :as ReactDom])
            #?(:cljs ["@tiptap/react" :refer (EditorContent useEditor)])
            #?(:cljs ["@tiptap/starter-kit" :refer (StarterKit)])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]))


#?(:cljs (def ReactRootWrapper
   (r/create-class
    {:component-did-mount (fn [this])
     :render (fn [this]
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

#?(:cljs (defn topic-reader [content]
           (let [editor (useEditor (clj->js {:extensions [StarterKit] :editable false :content content}))]
             (if (some? editor)
                 (do (. (. editor -commands) setContent content)
                     [:> EditorContent {:editor editor}])
                 [:div]))))
           ; TODO add 'Extract' button 
           ;      create with :topic/content and :topic/parent and :topic/original IDs
           ;      should insert after current topic?
           ; TODO add 'Delete Text' button to remove unnecessary text/html
           ;      enable when the current topic has selected text
           ; TODO swipe word left to hide everything up to then, swipe right to extract? (Serves same purpose as bookmarks)
           ; TODO if it has :topic/source - add button to 'View Original' in a new tab (highlight topic text on page like search engines do using a fragment)

#?(:cljs (defn topic-reader-wrapper [content]
           [:f> topic-reader content]))

(e/defn TopicReader [{content :topic/content}]
  (e/client (with-reagent topic-reader-wrapper content)))

