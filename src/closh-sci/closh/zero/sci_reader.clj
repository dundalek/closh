(ns closh.zero.sci-reader
  (:require [edamame.impl.parser :as parser]
            [clojure.tools.reader.reader-types :as r]))

(defn read-clojure [opts reader]
  (let [;; edamame fails wtih pushback reader overflow when reader is *in*
        ;; TODO figure something better than this workaround
        reader (r/indexing-push-back-reader reader 2)
        ctx (-> (assoc opts :all true)
                (parser/normalize-opts)
                (assoc ::parser/expected-delimiter nil))
        c (r/peek-char reader)
        loc (parser/location reader)
        obj (parser/dispatch ctx reader c)
        ret (if (identical? reader obj)
              (parser/parse-next ctx reader)
              (if (instance? clojure.lang.IObj obj)
                (vary-meta obj merge loc)
                obj))]
    (if (identical? ret ::parser/eof)
      (or (:eof opts) ::parser/eof)
      ret)))
