#!/bin/sh

# resolve link from node wrapper to the real lumo binary
LUMO_BIN=$(which lumo)
if test -L "$LUMO_BIN"
then
  LUMO_BIN=$(dirname $(readlink -f "$LUMO_BIN"))/lumo
fi

export CLOSH_SOURCES_PATH=$(dirname $(dirname $(realpath "$0")))

# NODE_PATH seems to be missing when running as global binary
export NODE_PATH="$CLOSH_SOURCES_PATH/node_modules:$CLOSH_SOURCES_PATH/..:$NODE_PATH"

exec "$LUMO_BIN" --classpath "$CLOSH_SOURCES_PATH/src" --cache "$HOME/.lumo_cache" -m closh.main
