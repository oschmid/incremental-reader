(ns oschmid.incremental-reader.import-html-test

  (:require [clojure.string :refer [split]]
            [clojure.test :refer [deftest is]]
            [oschmid.incremental-reader.import-html :as html]
            [hoeck.diff.lcs :refer [vec-diff]])
  (:import [java.net URI]))

(deftest uri-test
  (is (= nil (html/uri "plain text")))
  (is (= nil (html/uri "../relative/url/is/not/allowed")))
  (is (= (URI. "https://oliverschmid.ca") (html/uri "https://oliverschmid.ca")))
  (is (= (URI. "https://user:password@example.com/path?query=value#fragment")
         (html/uri "https://user:password@example.com/path?query=value#fragment")))
  ; examples from https://docs.oracle.com/javase/8/docs/api/java/net/URI.html
  (is (= nil (html/uri "mailto:java-net@java.sun.com")))
  (is (= nil (html/uri "news:comp.lang.java")))
  (is (= nil (html/uri "urn:isbn:096139210x")))
  (is (= (URI. "http://java.sun.com/j2se/1.3/")
         (html/uri "http://java.sun.com/j2se/1.3/")))
  (is (= nil (html/uri "docs/guide/collections/designfaq.html#28")))
  (is (= nil (html/uri "../../../demo/jfc/SwingSet2/src/SwingSet2.java")))
  (is (= nil (html/uri "file:///~/calendar")))
  ; from https://docs.oracle.com/javase/8/docs/api/java/net/URL.html
  (is (= nil (html/uri "http://foo.com/hello world/")))
  (is (= (URI. "http://foo.com/hello%20world")
         (html/uri "http://foo.com/hello%20world"))))

(defn diff [expected actual]
  (->> (vec-diff (split expected #"\n") (split actual #"\n"))
       (map (fn [[op line]]
              (case op
                nil ""
                :old (str "-" line)
                :new (str "+" line))))
       (apply str)))

(deftest clean-doc-test
  (let [expected (.trim (slurp "test/oschmid/incremental_reader/pre-push-checklist-clean.html"))
        actual (html/clean (slurp "test/oschmid/incremental_reader/pre-push-checklist.html"))]
    (is (empty? (diff expected actual)))))

