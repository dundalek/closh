
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
