#!/bin/sh
# Starts the AlphaTiming text UI in this terminal.
self=$0
while [ -L "$self" ]; do
    link=$(readlink "$self")
    case $link in /*) self=$link ;; *) self=$(dirname "$self")/$link ;; esac
done
. "$(dirname "$self")/lib/launch.sh"
launch alphatiming-tui.jar org.lisasp.alphatiming.tui.TuiLauncher "$@"
