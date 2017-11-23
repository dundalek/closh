
## Unreleased features in development version

### History

History gets saved to the file `~/.closh/closh.sqlite` which is a SQLite database.

Use <kbd>up</kbd> and <kbd>down</kbd> arrows to cycle through history. First history from a current session is used, then history from all other sessions is used.

If you type some text and press <kbd>up</kbd> then the text will be used to match beginning of the command (prefix mode). Pressing <kbd>ctrl-r</kbd> will switch to matching anywhere in the command (substring mode). The search is case insensitive.

While in the history search mode you can use following controls:
- <kbd>enter</kbd> to accept the command and execute it
- <kbd>tab</kbd> to accept the command but have ability to edit it
- <kbd>esc</kbd> cancel search keeping the initial text
- <kbd>ctrl-c</kbd> cancel search and resetting the initial text

The `history` builtin is not yet implemented. If you need to list history you can query the database directly like:

```sh
sqlite3 ~/.closh/closh.sqlite "SELECT command FROM history ORDER BY id ASC"
```

This would be a perfect candidate for an alias when support for aliases is added.

## Number of LOC for existing shells:

```
wc -l bash/*.{h,c} | tail -1
73460 total

wc -l zsh/Src/*.{h,c} | tail -1
79214 total

wc -l fish-shell/src/*.{h,cpp} | tail -1
59027 total

wc -l mksh/*.{h,c} | tail -1
34286 total

git clone git@github.com:elves/elvish.git
find elvish -name '*.go' | grep -v '^elvish/vendor/\|_test.go$' | xargs wc -l | tail -n 1
21673 total

git clone git@github.com:PowerShell/PowerShell.git
find PowerShell/src/ -name "*.cs" | xargs wc -l | tail -n 1
744909 total

git clone git@github.com:xonsh/xonsh.git
find xonsh/xonsh -name "*.py" | grep -v 'xonsh/xonsh/ply' | xargs wc -l | tail -n 1
32283 total
```
