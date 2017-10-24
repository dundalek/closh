
# closh - Command shell based on Clojure

Closh combines the best of traditional unix shells with the power of Clojure. It aims to be a modern alternative to bash.

Why try to reinvent bash?
- Bash has obscure syntax for non-trivial operations and lots of WTF moments.
- It treats everything as text while we mostly need to manipulate structured information.
- It is a large codebase which makes it difficult to hack on it and try innovative ideas. Therefore the shell did not improve much in recent decades.

Why shell based on Clojure(Script)?
- Clojure's has a simple syntax and well-thought design which makes it pleasurable to work with.
- Its extensive collection of powerful functions for data manipulation is suitable to provide solutions for daily tasks.
- Write shell scripts in a language you use daily for development so you don't have to google arcane shell constructs every time you need to do anything but the simplest tasks.
- Less amount and more composable code allows to experiment with new features and ideas.

**Warning:** *Closh is still in a early stage and under a heavy development. It is not ready for daily use yet since it is quite easy to get it to crash. At this moment I am most interested in gathering feedback and use cases to help make the best possible design trade-offs. Closh is tested on Linux, should run on macOS too. Windows who knows.*

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

```clojure
$ (+ 1 2)
; => 3
```

The power comes from combining shell commands and Clojure:

```clojure
$ echo hi | (str/upper-case)
; => HI

$ ls *.txt |> (reverse)

; Count number of files grouped by first letter sorted by highest count first
$ ls |> (group-by first) | (map #(update % 1 count)) | (sort-by second) | (reverse)
```

Read the [guide](./doc/guide.md) to learn more.

## Roadmap

Features needed for daily use:

- [x] Commmand execution
- [x] Pipes
- [x] IO Redirects
- [x] REPL
- [ ] Persistent history
- [ ] Dynamic prompt
- [ ] Autocomplete
- [ ] Handle common erros
- [ ] Testing and stability
- [ ] Configuration and init scripts
- [ ] Signal control
- [ ] Job control
- [ ] Environment variable integration
- [ ] Builtin utility functions

Cool things and ideas for exploration:

- [ ] Syntax highlighting
- [ ] Automatic alias suggestion
- [ ] [Interactive command-line interfaces](http://dundalek.com/entropic/combining-cli-and-gui/)

## Tech details

Closh runs ClojureSript on node.js via [lumo](https://github.com/anmonteiro/lumo/) REPL. In order to be somewhat bashward compatible there is a command mode which transforms top-level forms in a macro-like way.

Thanks to Clojure's syntax for symbols supporting almost all characters we don't need to roll out a custom parser to support convenient unquoted notation for filenames and switches. Only customization done to a built-in reader is the support multiple slashes in a symbol, which is required for nested directories.

## Development

Clone the repo and install dependencies

```
git clone git@github.com:dundalek/closh.git
cd closh
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

Generate API documentation into `doc/api`
```
lein doc
```

## Copyright & License

Copyright (c) Jakub Dundalek

Distributed under the Eclipse Public License 1.0.
