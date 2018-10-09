## Tech details

Closh runs ClojureSript on node.js via [lumo](https://github.com/anmonteiro/lumo/) REPL. In order to be somewhat bashward compatible there is a command mode which transforms top-level forms in a macro-like way.

Thanks to Clojure's syntax for symbols supporting almost all characters we don't need to roll out a custom parser to support convenient unquoted notation for filenames and switches. Only customization done to a built-in reader is the support multiple slashes in a symbol, which is required for nested directories.

## Architecture

To get an idea of the scope of an implementation, here is a basic overview of closh components:
- **reader** - Since most shell operators can be just read as symbols writing custom reader could be avoided so reading is done with `tools.reader`. It needed minor tweaks to read things like IP addresses (e.g. `8.8.8.8` which would be an invalid number) or paths (e.g. `/a/path/to/file` which would be an invalid symbol). Thanks to `tools.reader` being written in Clojure the customizations were fairly simple.
- **parser** - `clojure.spec` is used for parsing, specs look like a grammar for parser generators. Doing conform transforms the input into AST.
- **compiler** - This is just a data transformation using plain clojure functions. Generated code uses the `pipeline`.
- **pipeline** - Functionality to run sequence of processes and pipe data.
- **platform** - Abstracted functions to spawn processes and redirect IO. Abstractions for JVM and node.js provide almost identical API which the pipeline utilizes.
- **frontend** - This provides the repl for interactive use. For CLJS version we use internal node.js `readline` module. The CLJ version runs on `clojure.main/repl` with `rebel-readline`.

The **reader** customization, **platform** and **frontends** have separate implementations for CLJ and CLJS versions. The code for **parser**, **compiler** and **pipeline** is shared (using common CLJC files).
