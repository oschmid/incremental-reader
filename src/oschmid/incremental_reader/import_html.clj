(ns oschmid.incremental-reader.import-html

  (:import [java.net MalformedURLException URISyntaxException URL]
           [org.jsoup Jsoup]
           [org.jsoup.nodes Document]
           [org.jsoup.safety Safelist]))

(defn uri "Absolute URL or nil" [s]
  (try
    (let [u (.toURI (URL. s))]
      (if (some? (.getHost u)) u nil))
    (catch URISyntaxException _ nil)
    (catch MalformedURLException _ nil)))

; Allows a full range of text and structural body HTML:
;   a, b, blockquote, br, caption, cite, code, col, colgroup, dd, div, dl,
;   dt, em, h1, h2, h3, h4, h5, h6, i, img, li, ol, p, pre, q, small, span,
;   strike, strong, sub, sup, table, tbody, td, tfoot, th, thead, tr, u, ul
(defn clean [doc]
  (-> (if (instance? Document doc) doc (Jsoup/parse doc))
      (.body)
      (.html)
      (Jsoup/clean (Safelist/relaxed))))

(defn scrape "Scrape text and images" [url]
  (-> (Jsoup/connect url) ; default timeout is 30 seconds
      (.get)
      (clean)))
      ; TODO add domain specific filters (e.g. wikipedia) based on Anki IR plugin
      ; TODO share safelist with TopicReader schema

