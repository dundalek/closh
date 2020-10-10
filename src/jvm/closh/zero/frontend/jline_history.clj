(ns closh.zero.frontend.jline-history
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [closh.zero.service.history-common :refer [table-history table-session get-db-filename check-history-line]]
            [closh.zero.platform.process :as process])
  (:import [org.jline.reader History History$Entry]
           [java.time Instant]
           [java.util ListIterator]))

;; In case there is a need to debug this, run `git checkout c5f8e8f8b5440dcc0ebf91055cd2f1295e528629` to get the helper code which includes iterator implementations, in menory history or debugging proxy.

(defn flip-iterator [^ListIterator iterator]
  (reify ListIterator
    (hasNext [this]
      (.hasPrevious iterator))
    (hasPrevious [this]
      (.hasNext iterator))
    (next [this]
      (.previous iterator))
    (previous [this]
      (.next iterator))
    (add [this e]
      (throw (UnsupportedOperationException.)))
    (nextIndex [this]
      (throw (UnsupportedOperationException.)))
    (previousIndex [this]
      (throw (UnsupportedOperationException.)))
    (remove [this]
      (throw (UnsupportedOperationException.)))
    (set [this e]
      (throw (UnsupportedOperationException.)))))

;; TODO: maybe use gen-class
(defn create-entry [index time line]
  (reify History$Entry
    (index [this] index)
    (time [this] time)
    (line [this] line)
    (toString [this]
      (format "%d: %s" index line))))

(defn row->entry [{:keys [idx time command]}]
  (create-entry idx (Instant/ofEpochMilli time) command))

(defn- init-database-tables-session [db-spec]
  (jdbc/db-do-commands db-spec [table-history table-session])
  (-> (jdbc/insert! db-spec :session {:time (System/currentTimeMillis)})
      (first)
      (get (keyword "last_insert_rowid()"))))

(defn ^History sqlite-history
  ([] (sqlite-history (get-db-filename)))
  ([db-file]
   (io/make-parents db-file)
   (let [db-spec {:classname "org.sqlite.JDBC"
                  :subprotocol "sqlite"
                  :subname db-file}
         session-id (init-database-tables-session db-spec)
         !index (atom 0)
         !all-count (atom 0)
         !all-last-id (atom 0)
         !session-count (atom 0)]
     (reify History
      ;; Attaching reader is just to customize history behavior by injecting options. It is extra coupling, we don't need that.
       (attach [this reader]
         #_(println "History: attach"))
       (load [this]
         #_(println "History: load"))
       (save [this]
         #_(println "History: save"))
       (purge [this]
         #_(println "History: purge"))
       (size [this]
         (+ @!all-count @!session-count))
       (index [this]
         @!index)
       (last [this]
         (dec (.size this)))
       (get [this index]
         (let [[op offset] (if (< index @!all-count)
                             ["!=" index]
                             ["=" (- index @!all-count)])
               query (str "SELECT command FROM history WHERE id IN (SELECT MAX(id) FROM history GROUP BY command) AND session_id " op " ? ORDER BY id LIMIT 1 OFFSET ?")]
           (-> (jdbc/query db-spec [query session-id offset])
               first
               :command)))
       (add [this time line]
         (when-let [line (check-history-line line)]
           (jdbc/insert! db-spec :history
                         {:session_id session-id
                          :time (.toEpochMilli time)
                          :command line
                          :cwd (process/cwd)}))
         (.moveToEnd this))
       (iterator [this index]
        ;; TODO: jline calls (.iterator n) for every movement, so this is probably very inefficient and a better way would be to implement custom iterator
         (-> ^clojure.lang.LazySeq
             (concat (jdbc/query db-spec
                                 ["SELECT time, command, ROW_NUMBER() OVER(ORDER BY id) - 1 + ? as idx FROM history WHERE id IN (SELECT MAX(id) FROM history GROUP BY command) AND session_id = ? ORDER BY id DESC" @!all-count session-id]
                                 {:row-fn row->entry})
                     (jdbc/query db-spec
                                 ["SELECT time, command, ROW_NUMBER() OVER(ORDER BY id) -1 as idx FROM history WHERE id IN (SELECT MAX(id) FROM history GROUP BY command) AND session_id != ? AND id <= ? ORDER BY id DESC" session-id @!all-last-id]
                                 {:row-fn row->entry}))
             (.listIterator (- (.size this) index))
             (flip-iterator)))
       (current [this]
         (let [index (.index this)]
           (if (>= index (.size this))
             ""
             (.get this index))))
       (previous [this]
         (.moveTo this (dec (.index this))))
       (next [this]
         (.moveTo this (inc (.index this))))
       (moveToFirst [this]
         (.moveTo this 0))
       (moveToLast [this]
         (.moveTo this (dec (.size this))))
       (moveTo [this index]
         (if (and (< index (.size this))
                  (<= 0 index)
                  (not= index (.index this)))
           (do (reset! !index index)
               true)
           false))
       (moveToEnd [this]
         (let [{:keys [lastid allcount]} (first (jdbc/query db-spec ["SELECT max(id) as lastid, count(*) as allcount FROM history WHERE id IN (SELECT MAX(id) FROM history GROUP BY command) AND session_id != ?" session-id]))
               {:keys [sessioncount]} (first (jdbc/query db-spec ["SELECT count(*) as sessioncount FROM history WHERE id IN (SELECT MAX(id) FROM history GROUP BY command) AND session_id = ?" session-id]))]
           (reset! !all-last-id lastid)
           (reset! !all-count allcount)
           (reset! !session-count sessioncount)
           (reset! !index (.size this))))))))
