(ns closh.tester
  (:require [clojure.tools.reader]
            [clojure.tools.reader.impl.commons]
            [closh.eval :refer [eval-cljs]]
            [closh.core :refer [get-out-stream wait-for-process wait-for-event handle-line]])
  (:require-macros [alter-cljs.core :refer [alter-var-root]]))

(def parse-symbol-orig clojure.tools.reader.impl.commons/parse-symbol)

(defn parse-symbol [token]
  (let [parts (.split token "/")
        symbols (map (comp second parse-symbol-orig) parts)
        pairs (->> (interleave parts symbols)
                   (partition 2))]
    (if (every? #(or (second %) (empty? (first %))) pairs)
      [nil (clojure.string/join "/" symbols)]
      parse-symbol-orig)))

; Hack reader to accept symbols with multiple slashes
(alter-var-root (var clojure.tools.reader.impl.commons/parse-symbol)
                (constantly parse-symbol))

(defn -main []
  (let [cmd (-> (seq js/process.argv)
                (nth 5)
                (pr-str))]
    (handle-line cmd eval-cljs)))

(set! *main-cli-fn* -main)
