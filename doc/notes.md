
Number of LOC for existing shells:

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
```
