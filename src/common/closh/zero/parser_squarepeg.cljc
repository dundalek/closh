(ns closh.zero.parser-squarepeg
  (:require [clojure.set])
  (:use squarepeg.core))

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

(defrule arg (mkpr #(not (op %))))

(defrule redirect (mkret (mkseq (mkopt (mkbind (mkpr number?) :fd))
                                (mkbind (mkpr redirect-op) :op)
                                (mkbind arg :arg))
                         (fn [b o] (select-keys b [:fd :op :arg]))))

(defrule cmd (mk1om (mkalt (mkret redirect
                                  (fn [b o] [:redirect (:ret b)]))
                           (mkret arg
                                  (fn [b o] [:arg (:ret b)])))))

(defrule pipeline (mkret (mkseq (mkopt (mkbind (mkpr #{'!}) :not))
                                (mkbind cmd :cmd)
                                (mkbind (mkzom
                                         (mkscope
                                           (mkret (mkseq (mkbind (mkpr pipe-op) :op)
                                                         (mkbind cmd :cmd))
                                                  (fn [b o] (select-keys b [:op :cmd])))))
                                        :cmds))
                         (fn [b o] (select-keys b [:not :cmd :cmds]))))

(defrule cmd-clause (mkret (mkseq (mkbind pipeline :pipeline)
                                  (mkbind (mkzom
                                            (mkscope
                                              (mkret (mkseq (mkbind (mkpr clause-op) :op)
                                                            (mkbind pipeline :pipeline))
                                                     (fn [b o] (select-keys b [:op :pipeline])))))
                                          :pipelines))
                           (fn [b o] (select-keys b [:pipeline :pipelines]))))

(defrule cmd-list (mkret (mkseq (mkbind cmd-clause :cmd)
                                (mkbind (mkzom
                                          (mkscope
                                            (mkret (mkseq (mkbind (mkpr cmd-op) :op)
                                                          (mkbind cmd-clause :cmd))
                                                   (fn [b o] (select-keys b [:op :cmd])))))
                                        :cmds))
                         (fn [b o] (select-keys b [:cmd :cmds]))))

(defn parse [coll]
  (:r (cmd-list coll {} {} {})))

(comment
  (:r (cmd-list '(echo a | tee > pom && echo x \; ls -l) {} {} {})))
