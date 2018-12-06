#!/usr/bin/env bash

tmp=tmp.jar

mv "$1" "$tmp"
echo '#!/usr/bin/env sh' > "$1"
echo 'exec java -jar "$0" "$@"' >> "$1"
cat "$tmp" >> "$1"
chmod +x "$1"
rm "$tmp"
