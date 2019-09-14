(ns closh.zero.frontend.jline-history
  (:import [org.jline.reader History History$Entry]
           [java.time Instant]))

(defn create-entry [index time line]
  (reify History$Entry
    (index [this] index)
    (time [this] time)
    (line [this] line)
    (toString [this]
      (format "%d: %s" index line))))

(defn memory-history []
  (let [!items (atom [])
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
               (create-entry (.size this) time line)))

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

(comment
  (def h (memory-history))

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

  (.hasNext iter)
  (str (.next iter)))
