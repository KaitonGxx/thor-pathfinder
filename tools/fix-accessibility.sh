#!/system/bin/sh
#
# Thor Pathfinder — switch its accessibility service back on.
#
# For when Android has switched Pathfinder off, or is showing it as on without
# having started it, and Shizuku isn't running so Pathfinder can't fix itself.
#
# Run it from the Thor's own Settings > Run script as Root, and pick this file.
#
# It touches three settings and nothing else:
#   boot_auto_launch_list           — AYN's "APP Auto Launch Manage" list
#   enabled_accessibility_services  — the list of accessibility services
#   accessibility_enabled           — Android's master switch for them
#
# Every other app and service already in those lists is kept exactly as it
# was. Read the whole thing before running it; it is short on purpose.

PKG=com.thorpathfinder.app
SERVICE=$PKG/com.thorpathfinder.app.PathfinderService
KEY=enabled_accessibility_services
AUTO=boot_auto_launch_list

# Take Pathfinder off AYN's "APP Auto Launch Manage" list. The apps switched
# on in that page can't have a service started unless they are in front, so
# on it Pathfinder's service never starts after a restart.
auto=$(settings get system $AUTO)
case ",$auto," in
    *",$PKG,"*)
        rest=""
        OLDIFS=$IFS
        IFS=','
        for app in $auto; do
            case "$app" in
                "$PKG") continue ;;
                "") continue ;;
            esac
            if [ -z "$rest" ]; then
                rest="$app"
            else
                rest="$rest,$app"
            fi
        done
        IFS=$OLDIFS
        if [ -z "$rest" ]; then
            settings delete system $AUTO > /dev/null
        else
            settings put system $AUTO "$rest"
        fi
        echo "Took Pathfinder off APP Auto Launch Manage."
        ;;
esac

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
