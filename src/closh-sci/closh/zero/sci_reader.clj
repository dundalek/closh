(ns closh.zero.sci-reader
  (:require [edamame.impl.parser :as parser]
            [clojure.tools.reader.reader-types :as r]))

(defn read-clojure [opts reader]
  (let [;; edamame fails with pushback reader overflow when reader is *in*
        ;; TODO figure something better than this workaround
        reader (r/indexing-push-back-reader reader 2)
        ctx (-> (assoc opts :all true)
                (parser/normalize-opts)
                (assoc ::parser/expected-delimiter nil))
        v (parser/parse-next ctx reader)]
    (if (identical? v ::parser/eof)
      (or (:eof opts) ::parser/eof)
      v)))
