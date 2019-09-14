(ns closh.zero.service.history
  (:require [clojure.string]
            [goog.object :as gobj]
            [sqlite3 :as sqlite]
            [path]
            [fs]
            [closh.zero.service.history-common :refer [table-history table-session get-db-filename]]))

(declare ^:dynamic db)
(declare ^:dynamic db-promise)
(declare ^:dynamic session-id)

(defn- init-database-file []
  (js/Promise.
    (fn [resolve reject]
      (let [db-file (get-db-filename)]
        (fs/mkdir (path/dirname db-file)
          (fn [err]
            (if (and err (not= (.-code err) "EEXIST"))
              (reject err)
              (resolve (sqlite/Database. db-file)))))))))

(defn- init-database-tables-session
  [db]
  (js/Promise.
    (fn [resolve reject]
      (.serialize db
        (fn []
          (.run db table-session)
          (.run db table-history)
          (.run db "INSERT INTO session VALUES (?, ?)"
                   #js[nil (js/Date.now)]
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

(defn- get-db [cb]
  (.then db-promise cb))

(defn add-history
  "Adds a new item to history."
  [command cwd cb]
  (get-db
    (fn [db]
      (.run db "INSERT INTO history VALUES (?, ?, ?, ?, ?)"
               #js[nil session-id (js/Date.now) command cwd]
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
