(ns closh.history)

(def os (js/require "os"))
(def path (js/require "path"))
(def fs (js/require "fs"))
(def sqlite (-> (js/require "sqlite3")
                (.verbose)))

(def db-file
  (path.join (os.homedir)
             ".closh"
             "closh.sqlite"))

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
                        (cb)))))))))))))
