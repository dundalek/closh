(ns closh.pipeline-test
  (:require [clojure.test :refer [deftest testing is are]]
            [closh.zero.platform.process :refer [shx]]
            [closh.zero.pipeline :refer [process-output wait-for-pipeline pipe pipe-multi pipe-map pipe-filter pipeline-value pipeline-condition]]))

(deftest test-pipeline
  (is (= (list "b" "a") (-> (shx "echo" ["a\nb"])
                            (pipe-multi (partial reverse)))))

  (is (= "B\nA\n") (-> (shx "echo" ["a\nb"])
                       (pipe-map clojure.string/upper-case)
                       (pipe (shx "sort" ["-r"]))
                       process-output))

  (is (= "2\n" (-> (shx "echo" ["a\nb"])
                   (pipe (shx "awk" ["END {print NR}"]))
                   process-output)))

  (is (= (list 3 2 1) (-> (list 1 2 3) (pipe reverse))))

  (is (= (list 1 3) (-> (list 1 2 3 4) (pipe-filter odd?))))

  ; '(echo hi 1 >& 2 | wc -l))
  (is (= "0\n" (-> (shx "echo" ["hix"] {:redir [[:out 2 "/dev/null"]
                                                [:set 1 2]]})
                   (pipe (shx "awk" ["END {print NR}"]))
                   process-output)))

  (are [x y] (= x (pipeline-value y))
    ; process to process - redirect stdout
    "ABC\n"
    ; "echo abc | tr \"[:lower:]\" \"[:upper:]\""
    (-> (shx "echo" ["abc"])
        (pipe (shx "tr" ["[:lower:]" "[:upper:]"])))

    ; process to fn - collect stdout
    "ABC\n"
    ; "echo abc | (clojure.string/upper-case)"))
    (-> (shx "echo" ["abc"])
        (pipe clojure.string/upper-case))

    ; process to sequence - split lines
    '("c" "b" "a")
    ; "echo -e \"a\\nb\\nc\" |> (reverse)"
    (-> (shx "printf" ["a\\nb\\nc"])
        (pipe-multi reverse))

    ; ; sequence to fn
    1
    ; "(list 1 2 3) | (first)"
    (-> (list 1 2 3)
        (pipe first))

    ; sequence to sequence
    '(3 2 1)
    ; "(list 1 2 3) | (reverse)"))
    (-> (list 1 2 3)
        (pipe reverse))

    ; sequence to process - join items
    "1\n2\n3\n"
    ; "(list 1 2 3) | cat -"
    (-> (list 1 2 3)
        (pipe (shx "cat" ["-"])))

    ; ; sequence of sequences could be tab separated?
    ; "1\t2\n3\t4\n"
    ; ; "(list [1 2] [3 4]) | cat -"
    ; (pipe (list [1 2] [3 4]) (shx "cat"))

    "{:a 123}"
    ; "(identity {:a 123}) | cat -"
    (pipe {:a 123} (shx "cat"))

    "{:a 123}\n{:b 456}\n"
    ; "(list {:a 123} {:b 456}) | cat -"
    (pipe (list {:a 123} {:b 456}) (shx "cat"))

    ; string to process
    "abc"
    ; "(str \"abc\") | cat -"
    (pipe "abc" (shx "cat" ["-"]))

    "abc\n"
    (pipe (shx "echo" ["abc"]) (shx "cat" ["-"]))

    ; string to sequence
    '("c" "b" "a")
    ; "(str \"a\\nb\\nc\") |> (reverse)"
    (pipe-multi "a\nb\nc" reverse)

    ; seqable to sequence
    '[1 2 3]
    ; "(identity [1 2 3] |> (identity)"
    (pipe-multi [1 2 3] identity)

    ; non-seqable to seqable - wrap in list
    '(false)
    ; "(identity false) |> (identity)"))
    (pipe-multi false identity)))
