(ns oschmid.incremental-reader.editor

  ; trick shadow into ensuring that client/server always have the same version
  ; all .cljc files containing Electric code must have this line!
  #?(:cljs (:require-macros [oschmid.incremental-reader.editor :refer [with-reagent]]))

  (:require #?(:cljs [reagent.core :as r])
            #?(:cljs ["react-dom/client" :as ReactDom])
            #?(:cljs ["@tiptap/react" :refer (EditorProvider)])
            #?(:cljs ["@tiptap/starter-kit" :refer (StarterKit)])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]))


#?(:cljs (def ReactRootWrapper
   (r/create-class
    {:component-did-mount (fn [this] (js/console.log "mounted"))
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

; TODO save changes to DB
#?(:cljs (defn editor [content]
           [:> EditorProvider {:extensions [StarterKit] :content content}]))

(e/defn Editor [extract]
  (e/client (let [content (:extract/content extract)]
              (with-reagent editor content))))
