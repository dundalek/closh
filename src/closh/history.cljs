(ns closh.history
  (:require [clojure.string]
            [goog.object :as gobj]))

(def ^:no-doc os (js/require "os"))
(def ^:no-doc path (js/require "path"))
(def ^:no-doc fs (js/require "fs"))
(def ^:no-doc sqlite (-> (js/require "sqlite3")
                         (.verbose)))

(def db-file
  "Path to the db file, defaults to ~/.closh/closh.sqlite"
  (path.join (os.homedir) ".closh" "closh.sqlite"))

(declare ^:dynamic db)
(declare ^:dynamic db-promise)
(declare ^:dynamic session-id)

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

(defn- init-database-file []
  (js/Promise.
    (fn [resolve reject]
     (fs.mkdir (path.dirname db-file)
       (fn [err]
         (if (and err (not= (.-code err) "EEXIST"))
           (reject err)
           (resolve (sqlite.Database. db-file))))))))

(defn- init-database-tables-session
  [db]
  (js/Promise.
    (fn [resolve reject]
      (.serialize db
        (fn []
          (.run db table-session)
          (.run db table-history)
          (.run db "INSERT INTO session VALUES (?, ?)"
                   #js[nil (Date.now)]
            (fn [err]
              (if err
                (reject err)
                (this-as t
                  (resolve (.-lastID t)))))))))))

(defn init-database
  "Creates the db connection and gets a new session id (creates the tables if they not exist)."
  []
  (let [p (-> (init-database-file)
              (.then #(do (set! db %)
                          (init-database-tables-session %)))
              (.then #(do (set! session-id %))))]
    (set! db-promise (.then p (fn [] db)))
    (.then p (fn [] {:db db :session-id session-id}))))

(defn get-db [cb]
  (.then db-promise cb))

(defn add-history
  "Adds a new item to history."
  [command cwd cb]
  (get-db
    (fn [db]
      (.run db "INSERT INTO history VALUES (?, ?, ?, ?, ?)"
               #js[nil session-id (Date.now) command cwd]
               cb))))

(defn search-history
  "Searches the history DB."
  [query history-state search-mode operator direction cb]
  (let [{:keys [index skip-session]} history-state
        escaped (clojure.string/replace query #"[%_]" #(str "\\" %))
        expr (case search-mode
               :prefix (str escaped "%")
               :substring (str "%" escaped "%")
               escaped)
        sql (str " SELECT id, command "
                 " FROM history "
                 " WHERE id IN (SELECT MAX(id) FROM history GROUP BY command) "
                    (when index (str " AND id " operator " $index "))
                    " AND session_id " (if skip-session "!=" "=") " $sid "
                    " AND command LIKE $expr ESCAPE '\\' "
                 " ORDER BY id " direction " LIMIT 1;")
        params #js{:$expr expr
                   :$sid session-id}]
    (when index (gobj/set params "$index" index))
    (get-db
      (fn [db]
        (.get db sql params
          (fn [err data]
            (cb err
                (when data
                  [(.-command data) (assoc history-state :index (.-id data))]))))))))

(defn search-history-prev
  "Searches for the previous item in history DB."
  [query history-state search-mode cb]
  (search-history query history-state search-mode "<" "DESC"
    (fn [err data]
      (if err
        (cb err)
        (if (and (not data)
                 (not (:skip-session history-state)))
          (search-history-prev query
                               (assoc history-state :skip-session true :index nil)
                               search-mode cb)
          (cb err data))))))

(defn search-history-next
  "Searches for the next item in history DB."
  [query history-state search-mode cb]
  (search-history query history-state search-mode ">" "ASC"
    (fn [err data]
      (if err
        (cb err)
        (if (and (not data)
                 (:skip-session history-state))
          (search-history-next query
                               (assoc history-state :skip-session false :index nil)
                               search-mode cb)
          (cb err data))))))
