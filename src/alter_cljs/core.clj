; Lifted from https://github.com/eyelidlessness/alter-cljs/blob/develop/src/alter_cljs/core.clj
; v0.2.0
; Copyright (C) 2015 Trevor Schmidt trevor@democratizr.com
; Distributed under DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE Version 2, December 2004

(ns alter-cljs.core
  (:refer-clojure :exclude [alter-var-root]))

(defmacro if-cljs
  "Return then if we are generating cljs code and else for Clojure code.
   http://blog.nberger.com.ar/blog/2015/09/18/more-portable-complex-macro-musing"
  [then else]
  (if (:ns &env) then else))

(defn var-seq? [x]
  (and (list? x) (= 'var (first x)) (symbol? (second x))))

(defn resolve-cljs-sym [env sym]
  (let [init (get-in env [:locals sym :init])
        form (:form init)]
    (cond
      (var-seq? form) (second form)
      form (recur (:env init) form)
      :else nil)))

(defmacro alter-var-root [x f]
  (let [var-seq? (var-seq? x)
        sym? (symbol? x)
        var-sym (cond
                  var-seq? (second x)
                  sym? (resolve-cljs-sym &env x)
                  :else nil)
        var-sym? (not (nil? var-sym))
        altered (list f var-sym)
        with-var-sym (when var-sym?
                       (list 'set! var-sym altered))

        throw* `(throw (ex-info "Expected var" {:got '~x}))

        [m ns-obj munged-name] (repeatedly gensym)
        js-assign (list 'let [m (list 'meta x)
                              ns-obj (list '-> m :ns 'find-ns '.-obj)
                              munged-name (list '-> m :name 'munge)]
                    (list 'js* "~{}[~{}] = ~{}.call(null, ~{}[~{}])"
                               ns-obj munged-name f ns-obj munged-name))

        cljs-assign (list 'try
                      (if var-sym? with-var-sym js-assign)
                      (list 'catch :default (gensym)
                        throw*))]
    `(if-cljs
       ~cljs-assign
       (clojure.core/alter-var-root ~x ~f))))
