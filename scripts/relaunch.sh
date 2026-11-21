#!/bin/sh
# Closes any running Polylingy (a dev run started by Gradle, or the packaged/installed
# Polylingy.app), then builds and starts the current code with `gradlew :app:run`.
# The twin of relaunch.ps1 for macOS (and Linux). Other Java processes (Gradle daemons,
# IDEs) are left alone: processes are matched by their command line or executable path only.
#
# Usage: scripts/relaunch.sh

root=$(cd "$(dirname "$0")/.." && pwd)

# A dev run: java with the main class on its command line. The packaged app: its launcher inside Polylingy.app.
patterns='com\.polylingy\.app\.MainKt
Polylingy\.app/Contents/MacOS/Polylingy'

pids=""
for pattern in $patterns; do
    for pid in $(pgrep -f "$pattern"); do
        echo "Stopping Polylingy (pid $pid): $(ps -o command= -p "$pid" | cut -c1-120)"
        pids="$pids $pid"
    done
done

if [ -n "$pids" ]; then
    kill $pids 2>/dev/null
    # Give them a few seconds to exit, then stop whatever is left for good.
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        alive=""
        for pid in $pids; do kill -0 "$pid" 2>/dev/null && alive="$alive $pid"; done
        [ -z "$alive" ] && break
        sleep 0.5
    done
    [ -n "$alive" ] && kill -9 $alive 2>/dev/null
fi

cd "$root" || exit 1
exec ./gradlew :app:run --console=plain
