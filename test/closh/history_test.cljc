(ns closh.history-test
  (:require [clojure.test :refer [deftest is are]]
            [closh.test-util.util :refer [with-tempfile]]
            #?(:cljs [closh.zero.service.history :as history]
               :clj [closh.zero.frontend.jline-history :as jhistory])))

(defn iter->seq [iter]
  (loop [coll []]
    (if (.hasPrevious iter)
      (recur (conj coll (.previous iter)))
      coll)))

(defn history->seq [h]
  (->>
    (iter->seq (.iterator h (.index h)))
    (map str)
    (into [])))

(deftest history
  ;; First get history from current session, then from other sessions
  (with-tempfile
    #(let [db-file %
              s1 (jhistory/sqlite-history db-file)
              s2 (jhistory/sqlite-history db-file)]
          (.add s1 "a")
          (.add s2 "b")
          (.add s1 "c")
          (.moveToEnd s2)
          (is (= ["2: c" "1: a" "0: b"] (history->seq s1)))
          (is (= ["2: b" "1: c" "0: a"] (history->seq s2)))))

  ;; Do do not add when line is starting with whitespace
  (is (= ["0: a"]
         (with-tempfile
           #(-> (doto (jhistory/sqlite-history %)
                  (.add "a")
                  (.add " b"))
                (history->seq)))))

  ;; Do do not add empty lines
  (is (= ["0: a"]
         (with-tempfile
           #(-> (doto (jhistory/sqlite-history %)
                  (.add "a")
                  (.add "  ")
                  (.add ""))
                (history->seq)))))

  ;; Trim whitespace
  (is (= ["0: a b"]
         (with-tempfile
           #(-> (doto (jhistory/sqlite-history %)
                  (.add "a b \n"))
                (history->seq)))))

  ;; No duplicates are returned
  (is (= ["1: a" "0: b"]
         (with-tempfile
           #(-> (doto (jhistory/sqlite-history %)
                  (.add "a")
                  (.add "b")
                  (.add "a"))
                (history->seq))))))
