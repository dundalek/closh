(ns closh.reader)

(defmacro patch-reader
  "Macro that expands to code patching `clojure.tools.reader` to accept symbols containing multiple slashes.

  The namespace `clojure.tools.reader.impl.commons` and function `alter-var-root` must be required in the namespace."
  []
  '(do

     (def ^:no-doc parse-symbol-orig clojure.tools.reader.impl.commons/parse-symbol)

     (defn ^:no-doc parse-symbol [token]
       (let [parts (.split token "/")
             symbols (map (comp second parse-symbol-orig) parts)
             pairs (->> (interleave parts symbols)
                        (partition 2))]
         (if (every? #(or (second %) (empty? (first %))) pairs)
           [nil (clojure.string/join "/" symbols)]
           parse-symbol-orig)))

     ; Hack reader to accept symbols with multiple slashes
     (alter-var-root
       (var clojure.tools.reader.impl.commons/parse-symbol)
       (constantly parse-symbol))))
