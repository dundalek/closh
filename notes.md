
## syntax

Command mode uses simple shell-like syntax

git commit -a

'(shx "git" (expand "commit") (expand "-a"))

---

Forms starting with parenthesis are considered as Clojure code

(+ 1 2)

---

Glob patterns and environment variables are expanded

ls *.txt

ls $HOME

variables by string?
ls $(str "HO" "ME")

or something like (env (str "HO" "ME"))

---

(sh git commit -a)

---

command substitution

echo (+ 1 2)
echo (sh date)


---

ls |> (reverse) | head

---

ls | #(str/replace % #"\.txt" ".md")

---

ls | (str/upper-case)

---

ls -a | grep "^\\."
ls -a |? #(= (first %) ".")

'(pipe
  (shx "ls" (expand "-a"))
  (shx "grep" (expand "^\."))


---

find ./repl/src -ls | sort -nrk 7 | head -3

---

find ./ops/src/main -name '*.scala' | xargs wc -l

---

ls -l | sed -n 1~2p

ls -l |>> (keep-indexed #(when (odd? (inc %1)) %2))

---


echo '(1 + sqrt(5))/2' | bc -l

(/ (+ 1 (Math.sqrt 5)) 2)

---

du -s * | sort -k1,1rn | head


du -s * |>> (sort-by #(-> (clojure.string/split % #"\s+") (first) (js/Number))) (reverse) (take 10)

du -s * | (columns) |>> (sort-by #(-> first num -)) (take 10)

'(->>
  (shx "du" (expand "-s") (expand "*"))
  (lines)
  (map columns)
  (sort-by #(-> first num -))
  (take 10))

---

diff <(sort L.txt) <(sort R.txt)

---

variable assignment

a='xyz'

(def a "xyz")

(set! a "xyz")

---

exporting variables so that they are available for sub-shells

export a='xyz'

do it via environment variables?

(def $a "xyz")

---

redirects

echo hi 1>&2 | wc -l


echo hi >> file.txt

ls > files.txt

ls |> (spit "files.txt")

---

process substitution

<(LIST)

or

>(LIST)

---


---

command status

! echo hi || echo FAILED

---

aliases - just functions?

alias ls='ls -F --color=auto'

unalias dh

---

options

set -o

just variables? use star *opt* notation?

---

job control

background:
cmd &

---

special variables

$args

$?	Expands to the exit status of the most recently executed foreground pipeline.
$$	Expands to the process ID of the shell.
$!	Expands to the process ID of the most recently executed background (asynchronous) command.

$0	Expands to the name of the shell or shell script.
$_	The underscore variable is set at shell startup and contains the absolute file name of the shell or script being executed as passed in the argument list. Subsequently, it expands to the last argument to the previous command, after expansion. It is also set to the full pathname of each command executed and placed in the environment exported to that command. When checking mail, this parameter holds the name of the mail file.

---

here docs?

cat <<< 'here string'

cat <<EOF
plain EOF terminator: $foo
EOF

---

Considering regex globbing

ls #"a[ab]+"



From Ammonite:

Callable
    f! thing is an alias for f(thing)

Pipeable
    thing |> f is an alias for f(thing)
    or (-> things f) thread-first
    |>> thread-last

    things | f is an alias for things map f
    things || f is an alias for things flatMap f / mapcat f
    things |? f is an alias for things filter f
    things |& f is an alias for things reduce f
    // things |! f is an alias for things foreach f


ls | head

(pipe (shx "ls") (shx "head"))

ls | (str/upper-case)

(pipe-map (shx "ls") str/upper-case)

ls | (str/upper-case) | head

(-> (shx "ls"))
    (pipe-map str/upper-case)
    (pipe (shx "head"))


ls -a |? #(= (first %) ".")

(-> (shx "ls" "-a"))
    (pipe-filter #(= (first %) "."))


## library

bash:
env - env variables
set - list local variables

in repl:
(dir cljs.user)


; command mode macro
(defmacro sh)
- automatically split lines when piping to clj code
  then automatically join lines when piping a sequence to command mode
  otherwise just pipe

(defn shx
  "execute a shell command"
  [cmd & args])

(defn expand [x])
  ; env vars
  ; glob

(def num js/Number

(defn lines [s]
  (clojure.string/split #"\n"))

(defn columns [s]
  (clojure.string/split #"\s+"))

(defn columns-tab [s]
  (clojure.string/split #"\t"))

(defn join-lines [coll])
  (clojure.string/join "\n" coll)

(defn join-columns [coll])
  (clojure.string/join "\t" coll)


(defn pipe [])

; return lazy-seq of maps
(defn ls [&args])

; recursive
(defn lsr [&args])

; built ins
(defn exit [code]
  (js/process.exit code))

; test
man test
test for files

make it as macro that expands or with bindings so that it does not polute global namespace?

(test (and (x file) (w? file)))
-> (and (text/x? file) (test/w? file))


executable?
(defn x? [f])

writable?
(defn w? [f])



logout
ctrl-d
