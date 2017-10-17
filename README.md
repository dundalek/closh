
# closh - Command shell based on Clojure

## Quick Start

Install closh with (requires [node.js](https://nodejs.org/))
```
npm install -g lumo-cljs closh
```

Start the shell:
```
closh
```

Run simple commands like you are used to:

```
$ echo hi

$ git status

$ ls -l *.txt
```

Commands starting with a parenthesis are evaluated as Clojure code:

```
$ (+ 1 2)
; => 3
```

The power comes from shell commands and Clojure:

```clojure
$ echo hi | (str/upper-case)
; => HI

$ ls *.txt |> (reverse)

; Count number of files grouped by first letter with highest count first
$ ls |> (group-by first) | (map (fn [[k v]] [k (count v)])) | (sort-by second) | (reverse)
$ ls |> (group-by first) | (map #(update % 1 count)) | (sort-by (second) | (reverse)
```

Read the [guide](./doc/guide.md) to learn more.

## Development

Clone the repo and install dependencies

```
git clone
npm install
```

Run the app
```
npm start
```

Run in dev mode reloading on changes
```
npm run dev
```
Run tests once
```
lein test
```
Re-run tests on change
```
lein test-auto
```

## Licence
