
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
echo (sh date)
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

### Sequnce of commands

```
bash:  ls; echo hi
closh: (sh ls) (sh echo hi)
```

## Reference

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
