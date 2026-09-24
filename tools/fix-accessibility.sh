#!/system/bin/sh
#
# Thor Pathfinder — switch its accessibility service back on.
#
# For when Android has switched Pathfinder off, or is showing it as on without
# having started it, and Shizuku isn't running so Pathfinder can't fix itself.
#
# Run it from the Thor's own Settings > Run script as Root, and pick this file.
#
# It touches two settings and nothing else:
#   enabled_accessibility_services  — the list of accessibility services
#   accessibility_enabled           — Android's master switch for them
#
# Every other service already in the list is kept exactly as it was. Read the
# whole thing before running it; it is short on purpose.

SERVICE=com.thorpathfinder.app/com.thorpathfinder.app.PathfinderService
KEY=enabled_accessibility_services

current=$(settings get secure $KEY)
case "$current" in
    null) current="" ;;
esac

# Rebuild the list without Pathfinder, keeping everything else in order. Done
# with a loop rather than grep so it works on any Thor's shell.
others=""
OLDIFS=$IFS
IFS=':'
for entry in $current; do
    case "$entry" in
        com.thorpathfinder.app/*) continue ;;
        "") continue ;;
    esac
    if [ -z "$others" ]; then
        others="$entry"
    else
        others="$others:$entry"
    fi
done
IFS=$OLDIFS

# Off, then on. The list has to actually change for Android to look at it
# again, which is what fixes the case where the switch is already on and
# nothing started.
settings put secure $KEY "$others"
sleep 1

if [ -z "$others" ]; then
    settings put secure $KEY "$SERVICE"
else
    settings put secure $KEY "$others:$SERVICE"
fi
settings put secure accessibility_enabled 1

sleep 2
echo "Accessibility services now:"
settings get secure $KEY
