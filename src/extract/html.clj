(ns extract.html
  (:import [org.jsoup Jsoup]
           [org.jsoup.safety Safelist]))

(defn extract [url] "Extract text and images"
  (Jsoup/connect url)
  (Safelist/basicWithImages))

; TODO use jsoup to extract highlighted/formatted text
; [mfornos/clojure-soup](https://github.com/mfornos/clojure-soup)
; [Clojure - Java Interop](https://clojure.org/reference/java_interop)