
## Quick Guide

Command mode uses simple shell-like syntax

```
git commit -a
```

Forms starting with parenthesis are considered as Clojure code

```
(+ 1 2)
```

Glob patterns and environment variables are expanded
```
ls *.txt

ls $HOME

cd ~/Downloads
```

Use output from a command or function as arguments to other command

```
echo (+ 1 2)
echo (sh-str date)
```

Piping output between commands

```
ls | head
```

Piping with functions works similarly to `->>` threading macro

```
ls | (clojure.string/upper-case)

ls | #(clojure.string/replace % #"\.txt" ".md")
```

Use special `|>` pipe operator to split input into sequence of lines

```
ls |> (reverse) | (take 5)
```

Redirects - note that there must be spaces around redirection operators `>` and `>>`

```
ls > files.txt

echo hi >> file.txt

ls 2 > files.txt
```

Command status

```
echo hi && echo OK

! echo hi || echo FAILED
```

## Examples

Most of helper utilities can be replaced with functions on sequences.

```clojure
bash:  ls | head -n 5
closh: ls |> (take 5)

bash:  ls | tail -n 5
closh: ls |> (take-last 5)

bash:  ls | tail -n +5
closh: ls |> (drop 4)

; Print filenames starting with "."
bash:  ls -a | grep "^\\."
closh: ls -a |> (filter #(re-find #"^\." %))

; Print only odd numbered lines counting from 1
bash:  ls | sed -n 1~2p
closh: ls |> (keep-indexed #(when (odd? (inc %1)) %2))

; Math
bash:  echo '(1 + sqrt(5))/2' | bc -l
closh: (/ (+ 1 (Math.sqrt 5)) 2)
```
### Control flow

For loops:

```bash
for f in /sys/bus/usb/devices/*/power/wakeup; do echo $f; cat $f; done
```
```clojure
ls /sys/bus/usb/devices/*/power/wakeup |> (map #(str % "\n" (sh-str cat (str %)))) | cat
```

If conditionals:

```bash
if test -f package.json; then echo file exists; else echo no file; fi
```
```clojure
echo (if (sh-ok test -f package.json) "file exists" "no file")
```

### Sequence of commands

```
bash:  ls; echo hi
closh: (sh ls) (sh echo hi)
```

## Reference

### Custom prompt

The prompt can be customized by defining `closh-prompt` function in `~/.closhrc` file.

For example you can use [powerline](https://github.com/banga/powerline-shell) prompt like this:

```clojure
(require-macros '[closh.core :refer [sh-str]])

(defn closh-prompt []
  (sh-str powerline-shell --shell bare))
```

Or you can reuse existing prompt from [fish](http://fishshell.com/) shell:

```clojure
(require-macros '[closh.core :refer [sh-str]])

(defn closh-prompt []
  (sh-str fish -c fish_prompt)
```

Bash [prompt format](http://www.tldp.org/HOWTO/Bash-Prompt-HOWTO/bash-prompt-escape-sequences.html) can be used via [decode-prompt](https://github.com/substack/decode-prompt) module. Install it with `npm install -g decode-prompt`. Then use it like:

```clojure
(require '[cljs.nodejs])

(def decode-prompt (js/require "decode-prompt"))

(def PS1 "\\[\\033[01;32m\\]\\u@\\h\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]\\$ ")

(defn closh-prompt []
  (decode-prompt PS1 #js{:env js/process.env}))
```

### Quoting

Prevent some expansion is same like bash with double-quoted string:
```
echo "*"
```

Disable expansion completely with a single quote:
```clojure
bash:  echo '$HOME'
closh: echo '$HOME ; notice there is only one quote

; if the quoted string has spaces wrap in double quotes and then prepend single quote
bash:  echo '$HOME $PWD'
closh: echo '"$HOME $PWD"
```

### Current known reader limitations

Following forms do not currently work:

- IP addresses (invalid number error)
  replace `ping 8.8.8.8`
  with `ping "8.8.8.8"`
- unquoted brace expansion (gets parsed as symbol and a map)
  replace `ls *.{cljc,clj}`
  with `ls (expand "*.{cljc,clj}")`
- tilde with slash
  e.g. `vim ~/.closhrc`
