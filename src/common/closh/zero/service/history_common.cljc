(ns closh.zero.service.history-common
  (:require #?(:cljs [path]
               :clj [clojure.java.io :as io])
            [closh.zero.platform.process :as process]))

(def ^:no-doc table-history
  "CREATE TABLE IF NOT EXISTS history (
 id INTEGER PRIMARY KEY,
 session_id INTEGER NOT NULL,
 time INTEGER NOT NULL,
 command TEXT NOT NULL,
 cwd TEXT NOT NULL
);")

(def ^:no-doc table-session
  "CREATE TABLE IF NOT EXISTS session (
 id INTEGER PRIMARY KEY,
 time INTEGER NOT NULL
);")

(defn get-db-filename []
  "Return path to the db file, defaults to ~/.closh/closh.sqlite"
  (let [parts [(process/getenv "HOME") ".closh" "closh.sqlite"]]
    #?(:cljs (apply path/join parts)
        :clj (-> (apply io/file parts)
                 (.getCanonicalPath)))))
