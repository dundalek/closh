(ns closh.history-test
  (:require [clojure.test :refer [deftest is are testing]]
            [closh.test-util.util :refer [with-tempfile]]
            [closh.zero.macros #?(:clj :refer :cljs :refer-macros) [chain->]]
            #?(:cljs [closh.zero.service.history-common :refer [check-history-line]])
            #?(:cljs [closh.test-util.util :refer-macros [with-async]])
            #?(:cljs [util])
            #?(:cljs [closh.zero.service.history :as history]
               :clj [closh.zero.frontend.jline-history :as jhistory])))

#?(:clj
   (do
     (defn iter->seq [iter]
       (loop [coll []]
         (if (.hasPrevious iter)
           (recur (conj coll (.previous iter)))
           coll)))

     (defn history->seq [h]
       (->>
        (iter->seq (.iterator h (.index h)))
        (map #(.line %))
        (into [])))))

#?(:cljs
   (do
     (def add-history-promise (util/promisify history/add-history))
     (def search-history-prev (util/promisify history/search-history-prev))
     (def search-history-next (util/promisify history/search-history-next))

     (defn history->seq
       ([h] (history->seq h "" nil :prefix []))
       ([h query history-state search-mode coll]
        (.then (search-history-prev h query history-state search-mode)
               (fn [data]
                 (if-let [[line history-state] data]
                   (history->seq h query history-state search-mode (conj coll line))
                   coll)))))))

(defn create-history [db-file]
  #?(:clj (jhistory/sqlite-history db-file)
     :cljs (history/init-database db-file)))

(defn add-history [h command]
  #?(:clj (.add h command)
     :cljs (if-let [command (check-history-line command)]
             (add-history-promise h command "")
             (js/Promise.resolve))))

(defn assert-history [expected h]
  #?(:clj (is (= expected (history->seq h)))
     :cljs (.then (history->seq h)
                  (fn [result]
                    (is (= expected result))))))

#?(:clj (defmacro with-async [form] form))

(deftest history-multi-sessions
  (testing "First get history from current session, then from other sessions"
    (with-tempfile
      (fn [db-file]
        (let [s1 (create-history db-file)
              s2 (create-history db-file)]
          (with-async
            (chain->
             (add-history s1 "a")
             (fn [_] (add-history s2 "b"))
             (fn [_] (add-history s1 "c"))
             #?(:clj (fn [_] (.moveToEnd s2)))
             (fn [_] (assert-history ["c" "a" "b"] s1))
             (fn [_] (assert-history ["b" "c" "a"] s2)))))))))

(deftest history-leading-whitespace
  (testing "Do do not add when line is starting with whitespace"
    (with-tempfile
      (fn [db-file]
        (let [s1 (create-history db-file)]
          (with-async
            (chain->
             (add-history s1 "a")
             (fn [_] (add-history s1 " b"))
             (fn [_] (assert-history ["a"] s1)))))))))

(deftest history-dont-add-empty
  (testing "Do do not add empty lines"
    (with-tempfile
      (fn [db-file]
        (let [s1 (create-history db-file)]
          (with-async
            (chain->
             (add-history s1 "a")
             (fn [_] (add-history s1 "  "))
             (fn [_] (add-history s1 ""))
             (fn [_] (assert-history ["a"] s1)))))))))

(deftest history-trim-whitespace
  (testing "Trim whitespace"
    (with-tempfile
      (fn [db-file]
        (let [s1 (create-history db-file)]
          (with-async
            (chain->
             (add-history s1 "a b \n")
             (fn [_] (assert-history ["a b"] s1)))))))))

(deftest history-no-duplicates
  (testing "No duplicates are returned"
    (with-tempfile
      (fn [db-file]
        (let [s1 (create-history db-file)]
          (with-async
            (chain->
             (add-history s1 "a")
             (fn [_] (add-history s1 "b"))
             (fn [_] (add-history s1 "a"))
             (fn [_] (assert-history ["a" "b"] s1)))))))))
