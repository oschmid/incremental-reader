(ns oschmid.incremental-reader.anki

  #?(:clj (:require [clj-http.client :as http]
                    [clojure.data.json :as json]))
  #?(:clj (:import [java.util.concurrent TimeoutException TimeUnit])))

#?(:clj (def API_URL "http://localhost:8765"))
#?(:clj (def API_VERSION 6))

#?(:clj (defn send-action [action params]
          (let [response (http/post API_URL {:socket-timeout 1000
                                             :connection-timeout 1000
                                             :content-type :json
                                             :body (json/write-str {:version API_VERSION
                                                                    :action action
                                                                    :params params})
                                             :accept :json})]
            (if-not (response :status)
              [:error (response :error-text)]
              (let [body (json/read-str (response :body))]
                (if (nil? (body :error))
                  [:ok (body :result)]
                  [:error (body :error)]))))))

#?(:clj (defn add-cloze [profile deck cloze]
          (send-action
           "multi"
           {:actions [{:action "loadProfile" :params {:name profile}}
                      {:action "addNote"
                       :params {:note {:deckName deck
                                       :modelName "Cloze"
                                       :fields {"Text" cloze}}}}]})))

