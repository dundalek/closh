(ns closh.history
  (:require [clojure.string]
            [goog.object :as gobj]))

(def os (js/require "os"))
(def path (js/require "path"))
(def fs (js/require "fs"))
(def sqlite (-> (js/require "sqlite3")
                (.verbose)))

(def db-file
  (path.join (os.homedir) ".closh" "closh.sqlite"))

(declare ^:dynamic db session-id)

(def table-history
  "CREATE TABLE IF NOT EXISTS history (
 id INTEGER PRIMARY KEY,
 session_id INTEGER NOT NULL,
 time INTEGER NOT NULL,
 command TEXT NOT NULL,
 cwd TEXT NOT NULL
);")

(def table-session
  "CREATE TABLE IF NOT EXISTS session (
 id INTEGER PRIMARY KEY,
 time INTEGER NOT NULL
);")

(defn add-history [cmd cwd cb]
  (.run db "INSERT INTO history VALUES (?, ?, ?, ?, ?)"
           #js[nil session-id (Date.now) cmd cwd]
           cb))

(defn load-history [cb]
  (.all db "SELECT * from history ORDER BY id DESC" cb))

(defn init-database [cb]
  (fs.mkdir (path.dirname db-file)
    (fn [err]
      (if (and err (not= (.-code err) "EEXIST"))
        (cb err)
        (do
          (set! db (sqlite.Database. db-file))
          (.serialize db
            (fn []
              (.run db table-session)
              (.run db table-history)
              (.run db "INSERT INTO session VALUES (?, ?)"
                       #js[nil (Date.now)]
                (fn [err]
                  (if err
                    (cb err)
                    (this-as t
                      (do
                        (set! session-id (.-lastID t))
                        (cb nil db session-id)))))))))))))

(defn search-history [query history-state search-mode operator direction cb]
  (let [escaped (clojure.string/replace query #"[%_]" #(str "\\" %))
        expr (case search-mode
               :prefix (str escaped "%")
               :substr (str "%" escaped "%")
               escaped)
        index (:index history-state)
        sql (str "SELECT id, command FROM history WHERE command LIKE $expr ESCAPE '\\' "
                 (when index (str " AND id " operator " $index "))
                 " ORDER BY id " direction " LIMIT 1;")
        params #js{:$expr expr}]
    (when index (gobj/set params "$index" index))
    (.get db sql params
      (fn [err data]
        (cb err
            (when data
              [(.-command data) (assoc history-state :index (.-id data))]))))))

(defn search-history-prev [query history-state search-mode cb]
  (search-history query history-state search-mode "<" "DESC" cb))

(defn search-history-next [query history-state search-mode cb]
  (search-history query history-state search-mode ">" "ASC" cb))
