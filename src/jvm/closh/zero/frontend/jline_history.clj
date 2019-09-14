(ns closh.zero.frontend.jline-history
  (:require [clojure.java.jdbc :as jdbc])
  (:import [org.jline.reader History History$Entry]
           [java.time Instant]))

(def db-spec
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "database.sqlite"})

;; TODO: use gen-class
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
        (.listIterator @!items index))
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

(defn sqlite-history []
  (let [!index (atom 0)]
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
        (-> (jdbc/query db-spec ["SELECT count(*) AS result FROM history"])
          first
          :result))
      (index [this]
        @!index)
      (last [this]
        (dec (.size this)))
      (get [this index]
        (-> (jdbc/query db-spec ["SELECT command FROM history ORDER BY id LIMIT 1 OFFSET ?" index])
          first
          :command))
      (add [this time line]
        (jdbc/insert! db-spec :history
                      {:session_id 1 ;; TODO: session
                       :time (.toEpochMilli time)
                       :command line
                       :cwd ""}) ;; TODO: cwd
        (.moveToEnd this))

      (iterator [this index]
        (-> (jdbc/query db-spec
                        ["SELECT time, command, ROW_NUMBER() OVER(ORDER BY id) -1 as idx FROM history ORDER BY id"]
                        {:row-fn row->entry})
            (.listIterator index)))
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
