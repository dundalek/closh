(ns closh.zero.parser-squarepeg
  (:require [clojure.set]
            [squarepeg.core :as g]))

(def ^:no-doc redirect-op
  "Set of symbols of redirection operators"
  #{'> '< '>> '&> '&>> '<> '>&})

(def ^:no-doc pipe-op
  "Set of symbols of pipe operators"
  #{'| '|> '|? '|&})

(def ^:no-doc clause-op
  "Set of symbols of command clause operators (conditional execution with `&&` and `||`)"
  #{'|| '&&})

(def ^:no-doc cmd-op
  "Set of symbols of operators separating commands"
  #{\;}) ; TODO '&

(def ^:no-doc op
  "Set of symbols of all operators"
  (clojure.set/union redirect-op pipe-op clause-op cmd-op))

(g/defrule arg (g/mkpr #(not (op %))))

(g/defrule redirect
  (g/mkret (g/mkseq (g/mkopt (g/mkbind (g/mkpr number?) :fd))
                    (g/mkbind (g/mkpr redirect-op) :op)
                    (g/mkbind arg :arg))
           (fn [b o] (select-keys b [:fd :op :arg]))))

(g/defrule cmd
  (g/mk1om (g/mkalt (g/mkret redirect
                             (fn [b o] [:redirect (:ret b)]))
                    (g/mkret arg
                             (fn [b o] [:arg (:ret b)])))))

(g/defrule pipeline
  (g/mkret (g/mkseq (g/mkopt (g/mkbind (g/mkpr #{'!}) :not))
                    (g/mkbind cmd :cmd)
                    (g/mkbind (g/mkzom
                               (g/mkscope
                                (g/mkret (g/mkseq (g/mkbind (g/mkpr pipe-op) :op)
                                                  (g/mkbind cmd :cmd))
                                         (fn [b o] (select-keys b [:op :cmd])))))
                              :cmds))
           (fn [b o] (select-keys b [:not :cmd :cmds]))))

(g/defrule cmd-clause
  (g/mkret (g/mkseq (g/mkbind pipeline :pipeline)
                    (g/mkbind (g/mkzom
                               (g/mkscope
                                (g/mkret (g/mkseq (g/mkbind (g/mkpr clause-op) :op)
                                                  (g/mkbind pipeline :pipeline))
                                         (fn [b o] (select-keys b [:op :pipeline])))))
                              :pipelines))
           (fn [b o] (select-keys b [:pipeline :pipelines]))))

(g/defrule cmd-list
  (g/mkret (g/mkseq (g/mkbind cmd-clause :cmd)
                    (g/mkbind (g/mkzom
                               (g/mkscope
                                (g/mkret (g/mkseq (g/mkbind (g/mkpr cmd-op) :op)
                                                  (g/mkbind cmd-clause :cmd))
                                         (fn [b o] (select-keys b [:op :cmd])))))
                              :cmds))
           (fn [b o] (select-keys b [:cmd :cmds]))))

(defn parse [coll]
  (:r (cmd-list coll {} {} {})))

(comment
  (:r (cmd-list '(echo a | tee > pom && echo x \; ls -l) {} {} {})))
