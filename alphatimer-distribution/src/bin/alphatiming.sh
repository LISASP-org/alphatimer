#!/bin/sh
# Starts the AlphaTiming window.
self=$0
while [ -L "$self" ]; do
    link=$(readlink "$self")
    case $link in /*) self=$link ;; *) self=$(dirname "$self")/$link ;; esac
done
. "$(dirname "$self")/lib/launch.sh"
launch alphatiming.jar org.lisasp.alphatiming.ui.AlphaTimingLauncher "$@"
