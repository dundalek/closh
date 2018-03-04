#!/bin/sh

export CLOSH_SOURCES_PATH=$(dirname $(dirname $(realpath "$0")))

LUMO_BIN="$CLOSH_SOURCES_PATH/node_modules/lumo-cljs/bin/lumo"

# NODE_PATH seems to be missing when running as global binary
export NODE_PATH="$CLOSH_SOURCES_PATH/node_modules:$CLOSH_SOURCES_PATH/..:$NODE_PATH"

exec "$LUMO_BIN" --classpath "$CLOSH_SOURCES_PATH/src" --cache "$HOME/.closh/cache/lumo" -m closh.main
