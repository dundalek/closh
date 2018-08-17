## Tech details

Closh runs ClojureSript on node.js via [lumo](https://github.com/anmonteiro/lumo/) REPL. In order to be somewhat bashward compatible there is a command mode which transforms top-level forms in a macro-like way.

Thanks to Clojure's syntax for symbols supporting almost all characters we don't need to roll out a custom parser to support convenient unquoted notation for filenames and switches. Only customization done to a built-in reader is the support multiple slashes in a symbol, which is required for nested directories.
