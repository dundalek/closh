
<img src="doc/img/logo/verticalversion.png" align="right" alt="closh" height="150px" style="border: none; float: right;">

# closh - Bash-like shell based on Clojure

[![Join the chat at https://gitter.im/closh/Lobby](https://badges.gitter.im/closh/Lobby.svg)](https://gitter.im/closh/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Build status on CircleCI](https://circleci.com/gh/dundalek/closh.svg?style=shield)](https://circleci.com/gh/dundalek/closh)

Closh combines the best of traditional unix shells with the power of [Clojure](https://clojure.org/). It aims to be a modern alternative to bash.

Demo showing how to execute commands and using Clojure to manipulate outputs in shell:

![closh demo screencast](./doc/img/demo.gif)

Why try to reinvent bash?
- Bash has obscure syntax for non-trivial operations and lots of WTF moments.
- It treats everything as text while we mostly need to manipulate structured information.
- It is a large codebase which makes it difficult to hack on it and try innovative ideas. Which is one of the reasons why the shell did not improve much in recent decades.

Why shell based on Clojure(Script)?
- Clojure's has a simple syntax and well-thought design which makes it pleasurable to work with.
- Its extensive collection of powerful functions for data manipulation is suitable to provide solutions for daily tasks.
- Write shell scripts in a language you use daily for development so you don't have to google arcane shell constructs every time you need to do anything but simplest tasks.
- Less amount and more composable code allows to experiment with new features and ideas.

**Warning:** *Closh is still in a early stage and under a heavy development. It is not ready for daily use yet since it is quite easy to get it to crash. At this moment I am most interested in gathering feedback and use cases to help make the best possible design trade-offs. Closh is tested on Linux, should run on macOS too. Windows who knows.*

## Community and Contribution

If you have feedback about a specific feature feel free to open an [issue](https://github.com/dundalek/closh/issues).  
For general discussion you can use [gitter chat](https://gitter.im/closh/Lobby).  

If you would like to contribute take look at [open issues](https://github.com/dundalek/closh/issues). Leave a comment if you find anything interesting  and we can improve the project together.

## Quick Start

**Windows** is currently **NOT supported!** If you know your way around with Windows, we need your help (see [#54](https://github.com/dundalek/closh/issues/54)).

Install closh (requires [node.js](https://nodejs.org/) version 9.x):
```
npm install -g closh
```

To install development version from master branch:
```
npm i -g dundalek/closh
```

Start the shell:
```
closh
```

Run simple commands like you are used to:

```
$ echo hi

$ git status

$ ls -l *.json
```

Commands starting with a parenthesis are evaluated as Clojure code:

```clojure
$ (+ 1 2)
; => 3
```

The power comes from combining shell commands and Clojure:

```clojure
$ echo hi | (clojure.string/upper-case)
; => HI

$ ls *.json |> (reverse)

; Count number of files grouped by first letter sorted by highest count first
$ ls |> (group-by first) | (map #(update % 1 count)) | (sort-by second) | (reverse)
```

If you like closh you can set it as your default shell (afterwards you need to log out and log back in):
```
which closh | sudo tee -a /etc/shells
chsh -s $(which closh)
```

## Documentation

- [Guide and Reference](./doc/guide.md) - Introduction to closh and basic configuration
- [Cookbook](./doc/cookbook.md) - Recipes for integration of other tools like NVM, Autojump, etc.
- [Design Principles](./doc/principles.md)
- [Notes on Existing Shells](./doc/notes.md)
- [Changelog](./CHANGELOG.md)

## Roadmap

- **Stage 1:** Initial proof-of-concept, try out if the combination of shell and Clojure could work. [COMPLETED]
- **Stage 2:** Implement essential functionality needed for daily use by early adopters. [COMPLETED]

#### Stage 3

Add additional features users expect from a shell. Then fix bugs and stabilize through continuous daily use. [IN PROGRESS]

- [ ] Add JVM support
- [ ] [REPL server](https://github.com/dundalek/closh/issues/55)
- [ ] Readline improvements
- [ ] Key bindings
- [ ] Handle common errors
- [ ] [Load files and libraries](https://github.com/dundalek/closh/issues/15)
- [ ] Job control
- [ ] Builtin utility functions
- [ ] Testing and stability

#### Stage 4

At this point we can start to experiment with innovative ideas and paradigms. For example:

- [ ] [Syntax highlighting](https://github.com/dundalek/closh/issues/21)
- [ ] Automatic abbreviation suggestion
- [ ] [Automatically generate autocompletions for unknown / custom programs](https://github.com/dundalek/closh/issues/13)
- [ ] [Interactive command-line interfaces](http://dundalek.com/entropic/combining-cli-and-gui/)
- [ ] Data helpers that automatically parse command output into data structures
- [ ] Structured output ala [TermKit](https://github.com/unconed/TermKit) or [lisp machines](https://youtu.be/o4-YnLpLgtk?t=3m12s)

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

Copyright (c) Jakub Dundalek and contributors

Distributed under the Eclipse Public License 1.0.
