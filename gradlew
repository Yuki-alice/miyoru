#!/bin/sh
# M0: prefer cached Gradle 9.7.0 when present, else fall back to wrapper dists
CACHED="$HOME/.gradle/wrapper/dists/gradle-9.7.0-bin/d4tj7w02tcgubx9zk9hbippn6/gradle-9.7.0/bin/gradle"
if [ -x "$CACHED" ]; then
  exec "$CACHED" "$@"
fi
exec gradle "$@"
