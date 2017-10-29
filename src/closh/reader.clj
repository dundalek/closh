(ns closh.reader)

(defmacro patch-reader
  "Macro that expands to code patching `clojure.tools.reader` to accept symbols containing multiple slashes.

  The namespace `clojure.tools.reader.impl.commons` and function `alter-var-root` must be required in the namespace."
  []
  '(do

     (defn ^:no-doc parse-symbol [token]
       (when-not (or (= "" token)
                     (.endsWith token ":")
                     (.startsWith token "::"))
         (let [parts (.split token "/")]
             (case (count parts)
               1 [nil (first parts)]
               2 [(first parts) (second parts)]
               [nil token]))))

     ; Hack reader to accept symbols with multiple slashes
     (alter-var-root
       (var clojure.tools.reader.impl.commons/parse-symbol)
       (constantly parse-symbol))))
