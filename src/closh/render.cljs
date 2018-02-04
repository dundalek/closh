(ns closh.render
  (:require [clojure.string :as str]
            [clojure.zip :as zip]
            [goog.object :as gobj]))

(def ^:no-doc termkit (js/require "terminal-kit"))
(def ^:no-doc term (.terminal termkit))
(def ^:no-doc deasync (js/require "deasync"))

(defn synchronize
  [f & args]
  (fn []
    (let [done  (atom false)
          value (atom nil)]
      (apply f (conj args (fn [error & values]
                            (reset! value {:error error :value values})
                            (reset! done true))))
      (.loopWhile deasync #(not @done))
      @value)))

(def term-get-cursor-location (synchronize (.bind term.getCursorLocation term)))

(def init-display
  [:text
   [:expr
    [:line :cursor]]])

(def state
  (atom
   {:display init-display
    :prompt "$ "}))

(defn insert-vector
  "Insert element e into vector v at position i"
  ([v e i] (into (conj (subvec v 0 i) e) (subvec v i))))

(defn zipper?
  "Checks to see if the object has zip/make-node metadata on it (confirming it
   to be a zipper."
  [obj]
  (contains? (meta obj) :zip/make-node))

(defn match-node
  "Predicate testing whether node `n` has a first element matching `x`"
  [n x]
  (and (coll? n) (= x (first n))))

(defn zip-select-nodes
  "Return a lazy seq of zipper locations starting from `loc` where `x` matches the first element"
  [loc x]
  (filter #(match-node (zip/node %) x)
          (take-while (complement zip/end?)
                      (iterate zip/next loc))))

(defn zip-select
  "Return the next zipper location at or after `loc` where `x` matches the first element. Return zip/end if not found."
  [loc x]
  (if (or (zip/end? loc) (match-node (zip/node loc) x))
    loc
    (recur (zip/next loc) x)))

(defn zip-select-next
  "Return the next zipper location after `loc` where `x` matches the first element. Return zip/end if not found."
  [loc x]
  (let [loc (zip/next loc)]
    (zip-select loc x)))

(defn select-nodes
  "Return a lazy seq of nodes from `tree` where `x` matches the first element"
  [tree x]
  (filter #(match-node % x)
          (tree-seq coll? rest tree)))

(defn select-children
  "Return a vector containing the immediate child nodes of `tree` where `x` matches the first element"
  [tree x]
  (vec (filter #(match-node % x)
               tree)))

(defn insert-prompt
  "Insert `prompt` into `tree`"
  [tree prompt]
  (-> tree
      zip/vector-zip
      (zip-select :expr)
      zip/up
      (zip/edit #(conj [(first %)] (into [:prompt prompt] (subvec % 1))))
      zip/node))

(defn get-opts
  "Get options map from the tree node"
  [tree]
  (let [opts (second tree)]
    (when (map? opts)
      opts)))

(defn get-opts-and-children
  "Get a map containing the options map and the children from the tree node"
  [tree]
  (if-let [opts (get-opts tree)]
    {:opts opts
     :children (subvec tree 2)}
    {:children (subvec tree 1)}))

(defmulti render*
  "Render a tree to a string"
  (fn [tree]
    (cond
      (nil? tree) :identity
      (keyword? tree) tree
      (string? tree) :identity
      (string? (first tree)) :string-seq
      (coll? (first tree)) :coll
      :else (first tree))))

(defmethod render* :identity
  [s]
  s)

(defmethod render* :string-seq
  [ss]
  (apply str ss))

(defmethod render* :coll
  [xs]
  (render* (flatten xs)))

(defmethod render* :line
  [[_ & els]]
  (str (apply str (map render* els)) "\n"))

(defmethod render* :cursor
  [_]
  nil)

(defmethod render* :prompt
  [[_ prompt child]]
  (render*
   (if-let [first-node (zip-select (zip/vector-zip child) :line)]
     (-> first-node
         (zip/edit #(insert-vector % prompt 1))
         (zip-select-next :line)
         ((fn [loc]
            (if (zip/end? loc)
              loc
              (-> loc
                  (zip/edit #(insert-vector % (str/join (repeat (count prompt) " ")) 1))
                  (zip-select-next :line)
                  recur))))
         zip/root))))

(defmethod render* :expr
  [tree]
  (render* (replace '{:expr :text} tree)))

(defmethod render* :text
  [[_ & els]]
  (let [s (apply str (map render* els))]
    (if (= (last s) "\n")
      (apply str (butlast s))
      s)))

(defn normalize-line
  "Make sure all strings in the line are expanded to characters. Makes it easier to manipulate."
  [line]
  (vec (flatten (map #(if (string? %) (seq %) %) line))))

(defn get-location
  "Get the location of the element `e` relative to the expression"
  [tree e]
  (let [lines (select-nodes tree :line)
        [y line] (first (keep-indexed #(when (some #{e} %2) [%1 %2]) lines))
        x (.indexOf (rest (normalize-line line)) e)]
    {:x x, :y y}))

(defmulti insert
  "Insert something into a tree (or zippered tree). If x and y aren't specified, insert at the cursor position"
  (fn [tree-or-zipper & args]
    (if (zipper? tree-or-zipper)
      :zipper
      :tree)))

(defmethod insert :tree
  [loc & args]
  (zip/root (apply insert (into [(zip/vector-zip loc)] args))))

(defmethod insert :zipper
  ([loc s]
   (let [{:keys [x y]} (get-location (zip/root loc) :cursor)]
     (insert loc s x y)))
  ([loc s x y]
   (let [lines (select-nodes (zip/node loc) :line)
         y (min (dec (count lines)) (max 0 y))
         x (min (dec (count (normalize-line (nth lines y)))) (max 0 x))]
     (zip/edit
      (nth (zip-select-nodes loc :line) y)
      #(insert-vector (normalize-line %) s (inc x))))))

(defn move-cursor
  "Move the cursor by the specified x (and optionally y) deltas"
  ([tree delta-x] (move-cursor tree delta-x 0))
  ([tree delta-x delta-y]
   (let [{:keys [x y]} (get-location tree :cursor)
         line-count (count (select-nodes tree :line))]
     ; first, remove the cursor from its old location
     (-> (zip/edit
          (nth (zip-select-nodes (zip/vector-zip tree) :line) y)
          #(let [line (normalize-line %)]
             (into (subvec line 0 (inc x)) (subvec line (+ 2 x)))))
         zip/up
         (insert :cursor (+ x delta-x) (+ y delta-y))
         zip/root))))

(defn parse
  "Parse a string into a tree. Optional x and y position the cursor."
  ([expr] (parse expr Infinity Infinity))
  ([expr x y]
   (insert
     (into [:text]
           (into [:expr]
                 (for [line (str/split expr #"\n")]
                   (normalize-line [:line line]))))
     :cursor
     x
     y)))

(defn string
  "Render a text tree to a string"
  ([tree] (string tree {}))
  ([tree opts]
   (render* tree)))

(defn terminal
  "Render a text tree to the terminal"
  ([tree] (terminal tree {}))
  ([tree opts]
   (let [s (string tree opts)
         {:keys [x y]} (get-location tree :cursor)
         {end-x :x end-y :y} (get-location (move-cursor tree Infinity Infinity) :cursor)
         delta-x (- end-x x)
         delta-y (- end-y y)
         prompt-x (count
                   (last
                    (str/split (apply str
                                      (take-while
                                       (complement coll?)
                                       (next (first (select-nodes tree :prompt)))))
                               #"\n")))]
     (when (> y 0) (.up term y)) ; up moves the cursor even with `0` as an argument
     (.eraseLine term)
     (.column term 1)
     (term s)
     (when (> delta-y 0) (.up term delta-y))
     (when (> delta-x 0) (.left term delta-x)))))

(defn terminate
  []
  (.grabInput term false)
  (term "\nExiting.\n")
  (js/process.exit 0))

(defn keypress
  "Handle a keypress event, dispatch as necessary, and return an updated state"
  [{:keys [display] :as state} name matches data]
  (if (.-isCharacter data) ;; key pressed is a regular character
    (update state :display insert name)
    (case name
      "UP" (update state :display move-cursor 0 -1)
      "DOWN" (update state :display move-cursor 0 1)
      "LEFT" (update state :display move-cursor -1)
      "RIGHT" (update state :display move-cursor 1)
      "ENTER" (do
                (println "\n ==>" (string (:display state)))
                (assoc state :display init-display))
      "CTRL_C" (terminate))))

(defn -main
  []
  (term "Tree rendering proof of concept:\n")
  (terminal (insert-prompt (:display @state) (:prompt @state)))
  (.on term "key"
       (fn [name matches data]
         (reset! state (keypress @state name matches data))
         (terminal (insert-prompt (:display @state) (:prompt @state)))))
  (.grabInput term true))

(set! *main-cli-fn* -main)

(comment
  ;; TODO: make these into tests
  
  (def d
    [:text
     [:expr
      [:line "(str 1"]
      [:line "     " :cursor "2)"]]])

  (def prompt "$ ")

  (insert-prompt d prompt)
  (take-while
   (complement coll?)
   (next (first (select-nodes (insert-prompt d prompt) :prompt))))

  (get-location d :cursor)
  (get-location (insert-prompt d prompt) :cursor)
  
  (select-nodes d :line)
  
  (map zip/node (zip-select-nodes (zip/vector-zip d) :line))

  (zip-select-next (zip/vector-zip d) :line)

  (-> d
      zip/vector-zip
      (zip-select-next :line)
      (zip-select-next :line)
      (zip-select-next :line))

  (normalize-line (second (select-nodes d :line)))
  
  (println (render* d))
  (println (string d))
  (println (render* (insert-prompt d prompt)))

  (zip/root (insert (zip/vector-zip d) \3 5 1))
  (insert d \3)
  (insert d \3 -1 -1)

  (move-cursor d 0)
  (move-cursor d -1)
  (move-cursor d 1)
  (move-cursor d 0 -1)
  (move-cursor d 0 1)
  (move-cursor d -Infinity -Infinity)
  (move-cursor d Infinity Infinity)

  (parse (string d))

  "end")
