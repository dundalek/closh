(ns closh.zero.frontend.jline-history
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [closh.zero.service.history-common :refer [table-history table-session #_get-db-filename]]
            [closh.zero.platform.process :as process])
  (:import [org.jline.reader History History$Entry]
           [java.time Instant]
           [java.util ListIterator]))

(defn get-db-filename []
  "database.sqlite")

(def db-spec
  {:classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname (get-db-filename)})

(defn create-iterator [iterator]
  (reify ListIterator
    (hasNext [this]
      (.hasNext iterator))
    (hasPrevious [this]
      (.hasPrevious iterator))
    (next [this]
      (.next iterator))
    (previous [this]
      (.previous iterator))
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

(defn flip-iterator [iterator]
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

#_(defn create-entry [index time line]
    (reify History$Entry
      (index [this]
        (println "Entry index: " (.toString this))
        index)
      (time [this]
        (println "Entry time: " (.toString this))
        time)
      (line [this] line
        (println "Entry line: " (.toString this))
        line)
      (toString [this]
        (format "%d: %s" index line))))

(defn row->entry [{:keys [idx time command]}]
  (create-entry idx (Instant/ofEpochMilli time) command))

(defn log [x]
  (println x)
  x)

(defn memory-history []
  (let [!items (atom [(create-entry 0 (Instant/now) "echo a")
                      (create-entry 1 (Instant/now) "echo b")
                      (create-entry 2 (Instant/now) "echo c")])
        !index (atom 0)]
    (reify History
      ;; Attaching reader is just to customize history behavior by injecting options. It is extra coupling, we don't need that.
      (attach [this reader]
        #_(println "History: attach"))
      (load [this]
        #_(println "History: load"))
      (save [this]
        #_(println "History: save"))
      ;;(write [this file incremental])
      ;;(append [this file incremental])
      ;;(read [this file incremental])
      (purge [this]
        #_(println "History: purge"))
      (size [this]
        (count @!items))
      (index [this]
        @!index)
      (last [this]
        (dec (.size this)))
      (get [this index]
        (.line (get @!items index)))
      (add [this time line]
        (swap! !items conj
               (create-entry (.size this) time line))
        (.moveToEnd this))

      (iterator [this index]
        (create-iterator (.listIterator @!items index)))
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
        (.moveTo (dec (.size this))))
      (moveTo [this index]
        (if (and (< index (.size this))
                 (<= 0 index)
                 (not= index (.index this)))
          (do (reset! !index index)
              true)
          false))
      (moveToEnd [this]
        (reset! !index (.size this))))))
      ;;(resetIndex [this]))))

(defn history-wrapper [h]
  (proxy [History] []
    ;; Attaching reader is just to customize history behavior by injecting options. It is extra coupling, we don't need that.
    (attach [reader]
      (println "History: attach"))
    (load []
      (println "History: load"))
    (save []
      (println "History: save"))
    ;;(write [this file incremental])
    ;;(append [this file incremental])
    ;;(read [this file incremental])
    (purge []
      (println "History: purge"))
    (size []
      (println "History: size")
      (log (.size h)))
    (index []
      (println "History: index")
      (log (.index h)))
    (last []
      (println "History: last")
      (log (.last h)))
    (get [index]
      (println "History: get " index)
      (log (.get h index)))
    (add [time line]
      (println "History: add" line time)
      (log (.add h time line)))

    (iterator [index]
      (println "History: iterator" index)
      (log (.iterator h index)))
    (current []
      (println "History: current")
      (log (.current h)))
    (previous []
      (println "History: previous")
      (log (.previous h)))
    (next []
      (println "History: next")
      (log (.next h)))
    (moveToFirst []
      (println "History: moveToFirst")
      (log (.moveToFirst h)))
    (moveToLast []
      (println "History: moveToLast")
      (log (.moveToLast h)))
    (moveTo [index]
      (println "History: moveTo" index)
      (log (.moveTo h index)))
    (moveToEnd []
      (println "History: moveToEnd")
      (log (.moveToEnd h)))))

(defn- init-database-tables-session [db-spec]
  (jdbc/db-do-commands db-spec [table-history table-session])
  (-> (jdbc/insert! db-spec :session {:time (System/currentTimeMillis)})
      (first)
      (get (keyword "last_insert_rowid()"))))

(defn sqlite-history []
  (io/make-parents (get-db-filename))
  (let [session-id (init-database-tables-session db-spec)
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
      ;;(write [this file incremental])
      ;;(append [this file incremental])
      ;;(read [this file incremental])
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
              query (str "SELECT command FROM history WHERE session_id " op " ? ORDER BY id LIMIT 1 OFFSET ?")]
          (-> (jdbc/query db-spec [query session-id offset])
            first
            :command)))
      (add [this time line]
        (jdbc/insert! db-spec :history
                      {:session_id session-id
                       :time (.toEpochMilli time)
                       :command line
                       :cwd (process/cwd)})
        (.moveToEnd this))
      (iterator [this index]
        ;; TODO: jline calls (.iterator n) for every movement, so this is probably very inefficient and a better way would be to implement custom iterator
        (-> (concat (jdbc/query db-spec
                                ["SELECT time, command, ROW_NUMBER() OVER(ORDER BY id) - 1 + ? as idx FROM history WHERE session_id = ? ORDER BY id DESC" @!all-count session-id]
                                {:row-fn row->entry})
                    (jdbc/query db-spec
                                ["SELECT time, command, ROW_NUMBER() OVER(ORDER BY id) -1 as idx FROM history WHERE session_id != ? AND id <= ? ORDER BY id DESC" session-id @!all-last-id]
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
        (.moveTo (dec (.size this))))
      (moveTo [this index]
        (if (and (< index (.size this))
                 (<= 0 index)
                 (not= index (.index this)))
          (do (reset! !index index)
              true)
          false))
      (moveToEnd [this]
        (let [{:keys [lastid allcount]} (first (jdbc/query db-spec ["SELECT max(id) as lastid, count(*) as allcount FROM history WHERE session_id != ?" session-id]))
              {:keys [sessioncount]} (first (jdbc/query db-spec ["SELECT count(*) as sessioncount FROM history WHERE session_id = ?" session-id]))]
          (reset! !all-last-id lastid)
          (reset! !all-count allcount)
          (reset! !session-count sessioncount)
          (reset! !index (.size this)))))))

(comment

 (def iter
   (let [session-id 2
         !all-count (atom 2)
         !all-last-id (atom 3)
         !session-count (atom 1)]
     (-> (concat (jdbc/query db-spec
                             ["SELECT time, command, ROW_NUMBER() OVER(ORDER BY id) - 1 + ? as idx FROM history WHERE session_id = ? ORDER BY id DESC" @!all-count session-id]
                             {:row-fn row->entry})
                 (jdbc/query db-spec
                             ["SELECT time, command, ROW_NUMBER() OVER(ORDER BY id) -1 as idx FROM history WHERE session_id != ? AND id <= ? ORDER BY id DESC" session-id @!all-last-id]
                             {:row-fn row->entry}))
       (.listIterator 0)
       #_(.listIterator (- (.size this) index))
       (flip-iterator))))


 (def s1 (doto (sqlite-history 1) (.moveToEnd)))
 (def s2 (doto (sqlite-history 2) (.moveToEnd)))

 (def iter (.iterator s1 (.index s1)))
 (def iter (.iterator s2 (.index s2)))

 (.next iter)
 (.previous iter)
 (.hasPrevious iter)
 (.hasNext iter)

 (.index s1)

 (.get s1 0)

 (.add s1 "a")
 (.add s1 "c")
 (.add s2 "b")

 (.size s1)
 (.size s2)

 (.moveToEnd s1)
 (.moveToEnd s2))

(comment
  (def h (memory-history))
  (def h (create-sqlite-history))

  (.size h)

  (.get h 0)
  (.current h)
  (.next h)
  (.previous h)
  (.index h)

  (.add h "a")
  (.add h "b")

  (def iter (.listIterator [:a :b :c]))

  (.next iter)

  (str)

  (def e (create-entry 0 (Instant/now) "abc"))

  (.line e)
  (.toString e)
  (str e)

  (def iter (.iterator h 0))

  (.hasPrevious iter)
  (.previous iter)
  (.hasNext iter)
  (.index (.next iter))

  (jdbc/query db-spec ["SELECT * FROM history"])

  (jdbc/query db-spec
                  ["SELECT id, time, command, ROW_NUMBER() OVER(ORDER BY id) -1 as idx FROM history ORDER BY id DESC"])

  (def iter (->>
              (jdbc/query db-spec
                          ["SELECT time, command, ROW_NUMBER() OVER(ORDER BY id) -1 as idx FROM history ORDER BY id LIMIT -1 OFFSET ?" 0]
                          {:row-fn row->entry})
              (.listIterator)))

  (Instant/ofEpochMilli (.toEpochMilli (Instant/now)))

  (jdbc/insert! db-spec :history {:session_id 1
                                  :time (System/currentTimeMillis)
                                  :command "echo hello world"
                                  :cwd "/home/me"}))
