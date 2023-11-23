(ns oschmid.incremental-reader.import-html

  (:import [java.net MalformedURLException URISyntaxException URL]
           [org.jsoup Jsoup]
           [org.jsoup.safety Safelist]))

(defn uri "Absolute URL or nil" [s]
  (try
    (let [u (.toURI (URL. s))]
      (if (some? (.getHost u)) u nil))
    (catch URISyntaxException _ nil)
    (catch MalformedURLException _ nil)))

(defn scrape "Scrape text and images" [url]
  (-> (Jsoup/connect url) ; default timeout is 30 seconds
      (.get)
      (.outerHtml)
      ; TODO add domain specific filters (e.g. wikipedia) based on Anki IR plugin
      (Jsoup/clean (Safelist/basicWithImages)))) ; TODO get safelist from Anki IR plugin

