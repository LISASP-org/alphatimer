#!/bin/sh
# Records from the command line; run without arguments for the usage.
self=$0
while [ -L "$self" ]; do
    link=$(readlink "$self")
    case $link in /*) self=$link ;; *) self=$(dirname "$self")/$link ;; esac
done
. "$(dirname "$self")/lib/launch.sh"
launch alphatiming-cli.jar org.lisasp.alphatiming.cli.RecordLauncher "$@"
