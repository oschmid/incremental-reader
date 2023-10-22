(ns incremental-reader-test
  (:require [clojure.test :refer [deftest is testing]]
            [incremental-reader :as ir]))

; keywords
(def ir-body :incremental-reader/body)
(def ir-extracts :incremental-reader/extracts)
(def ir-queue :incremental-reader/queue)
(def ir-source :incremental-reader/source)

; TODO use UUIDs
(deftest add-extract-test
  (testing "Exception is thrown if ID is not unique"
    (is (thrown-with-msg? IllegalArgumentException #"ID must be unique"
          (ir/add-extract 4 {ir-source "source.com" ir-body "Body"}
            {ir-queue [4] ir-extracts {4 {ir-source "source.com" ir-body "Body"}}}))))
  (testing "Extract is added to the head of the priority queue"
    (is (= {ir-queue [123] ir-extracts {123 {ir-source "source.com" ir-body "Body"}}}
           (ir/add-extract 123 {ir-source "source.com" ir-body "Body"} {})))
    (is (= {ir-queue [2 1] ir-extracts {1 {ir-source "source1.com" ir-body "Body1"}
                                        2 {ir-source "source2.com" ir-body "Body2"}}}
           (ir/add-extract 2 {ir-source "source2.com" ir-body "Body2"}
                           {ir-queue [1] ir-extracts {1 {ir-source "source1.com" ir-body "Body1"}}})))))
