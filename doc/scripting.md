
# Shell scripting

The `closh-zero.jar` binary accepts same arguments as [clojure.main CLI](https://clojure.org/guides/deps_and_cli). That includes all features like aliases and deps. You can refer to the options with help:

```sh
closh-zero.jar --help
```

## Running scripts

The most common way is running a one-off script by passing it as a command line argument like:
```sh
closh-zero.jar my-script.clj
```

Another common way is using the shebang:

```clojure
#!/usr/bin/env closh-zero.jar

ls (first *args*)
```

Then you can make the script executable and run it like:
```sh
chmod +x my-script.clj
./myscript.clj

```

## Writing scripts

Top-level commands can be written using the shell syntax:
```clojure
#!/usr/bin/env closh-zero.jar

echo "Working..."
sleep (rand-int 5)
echo "Done"
```

When you need to need to use the commands inside conditionals or functions, wrap them in a `sh` macro. Individual commands need to be separated with `\;`.

```clojure
#!/usr/bin/env closh-zero.jar

(defcmd do-work [interval]
  (sh echo "Working..." \;
      sleep (str interval) \;
      echo "Done"))

do-work (rand-int 5)
```

## Using arguments

To use CLI arguments you can use the `*args*` alias, which is a sequence of arguments passed by user.

```clojure
#!/usr/bin/env closh-zero.jar

echo "Number of arguments:" (count *args*)
```

For parsing command line arguments [tools.cli](https://github.com/clojure/tools.cli) works great. It is also possible to use any other library.

## Examples

As an example I ported [gifgen](https://github.com/lukechilds/gifgen) utility to closh. Compare the original [bash version](https://github.com/lukechilds/gifgen/blob/master/gifgen) with the [closh version](https://github.com/dundalek/dotfiles/blob/master/closh/bin/gifgen.clj).
