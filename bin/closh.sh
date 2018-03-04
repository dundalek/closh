#!/usr/bin/env sh

# Resolve a file to its canonical path, following symlinks
# https://stackoverflow.com/a/29835459
rreadlink() {

  target=$1 fname= targetDir= CDPATH=

  # Try to make the execution environment as predictable as possible:
  # All commands below are invoked via `command`, so we must make sure that `command`
  # itself is not redefined as an alias or shell function.
  # (Note that command is too inconsistent across shells, so we don't use it.)
  # `command` is a *builtin* in bash, dash, ksh, zsh, and some platforms do not even have
  # an external utility version of it (e.g, Ubuntu).
  # `command` bypasses aliases and shell functions and also finds builtins 
  # in bash, dash, and ksh. In zsh, option POSIX_BUILTINS must be turned on for that
  # to happen.
  { \unalias command; \unset -f command; } >/dev/null 2>&1
  [ -n "$ZSH_VERSION" ] && options[POSIX_BUILTINS]=on # make zsh find *builtins* with `command` too.

  while :; do # Resolve potential symlinks until the ultimate target is found.
    [ -L "$target" ] || [ -e "$target" ] || { command printf '%s\n' "ERROR: '$target' does not exist." >&2; return 1; }
    command cd "$(command dirname -- "$target")" # Change to target dir; necessary for correct resolution of target path.
    fname=$(command basename -- "$target") # Extract filename.
    [ "$fname" = '/' ] && fname='' # !! curiously, `basename /` returns '/'
    if [ -L "$fname" ]; then
      # Extract [next] target path, which may be defined
      # *relative* to the symlink's own directory.
      # Note: We parse `ls -l` output to find the symlink target
      #       which is the only POSIX-compliant, albeit somewhat fragile, way.
      target=$(command ls -l "$fname")
      target=${target#* -> }
      continue # Resolve [next] symlink target.
    fi
    break # Ultimate target reached.
  done
  targetDir=$(command pwd -P) # Get canonical dir. path
  # Output the ultimate target's canonical path.
  # Note that we manually resolve paths ending in /. and /.. to make sure we have a normalized path.
  if [ "$fname" = '.' ]; then
    command printf '%s\n' "${targetDir%/}"
  elif  [ "$fname" = '..' ]; then
    # Caveat: something like /var/.. will resolve to /private (assuming /var@ -> /private/var), i.e. the '..' is applied
    # AFTER canonicalization.
    command printf '%s\n' "$(command dirname -- "${targetDir}")"
  else
    command printf '%s\n' "${targetDir%/}/$fname"
  fi
}


CLOSH_SOURCES_PATH="$(dirname "$(dirname "$(rreadlink "$0")")")"
export CLOSH_SOURCES_PATH

# NODE_PATH seems to be missing when running as global binary
NODE_PATH="$CLOSH_SOURCES_PATH/node_modules$([ -n "$NODE_PATH" ] && printf ":%s" "$NODE_PATH")"
export NODE_PATH

exec lumo --classpath "$CLOSH_SOURCES_PATH/src" --cache "$HOME/.lumo_cache" -m closh.main
