#!/system/bin/sh
#
# Thor Pathfinder's watchdog.
#
# Pathfinder starts this through Shizuku, so it runs as the shell user and
# belongs to Shizuku rather than to Pathfinder. That is the whole point: when
# something force-stops or kills Pathfinder, this keeps running and switches
# its accessibility service back on.
#
# It is opt-in. It only ever adds Pathfinder's own component to the
# accessibility list and only ever takes Pathfinder off AYN's auto launch
# list, and it stops the moment the marker file is gone. Everything it does
# is here.
#
# Started with:  setsid sh watchdog.sh <seconds> &
# Stopped with:  rm <marker>

MARK=/data/local/tmp/thorpathfinder-watchdog.on
LOG=/data/local/tmp/thorpathfinder-watchdog.log
PKG=com.thorpathfinder.app
SERVICE=$PKG/com.thorpathfinder.app.PathfinderService
KEY=enabled_accessibility_services
AUTO=boot_auto_launch_list
EVERY=${1:-5}

note() {
    echo "$(date '+%m-%d %H:%M:%S')  $1" >> "$LOG"
    # Keep the log short enough to paste into a bug report.
    tail -n 40 "$LOG" > "$LOG.tmp" 2>/dev/null && mv "$LOG.tmp" "$LOG"
}

# Prints list $1, whose entries are separated by $2, without the entries that
# match pattern $3, keeping the rest in order.
without() {
    kept=""
    IFS=$2
    for entry in $1; do
        case "$entry" in
            $3|"") continue ;;
        esac
        if [ -z "$kept" ]; then
            kept="$entry"
        else
            kept="$kept$2$entry"
        fi
    done
    echo "$kept"
}

# The first app in front, to leave settings alone while someone may be
# changing them on purpose: a watchdog that fights the user is a bug.
in_front() {
    dumpsys activity activities 2>/dev/null | grep -m1 'ResumedActivity'
}

note "watchdog started, checking every ${EVERY}s"

while [ -f "$MARK" ]; do
    sleep $EVERY
    [ -f "$MARK" ] || break

    # AYN's "APP Auto Launch Manage" page keeps the apps switched on in it in
    # a system setting, and the framework won't start a service for any of
    # them unless that app is in front. On it, Pathfinder's service never
    # starts after a restart, whatever the switch says. Take Pathfinder off,
    # keeping every other app, unless AYN's settings are open.
    unblocked=0
    auto=$(settings get system $AUTO)
    case ",$auto," in
        *",$PKG,"*)
            case "$(in_front)" in
                *com.odin.settings*)
                    note "on AYN's auto launch list while its settings were open, leaving it alone"
                    ;;
                *)
                    rest=$(without "$auto" ',' "$PKG")
                    if [ -z "$rest" ]; then
                        settings delete system $AUTO > /dev/null
                    else
                        settings put system $AUTO "$rest"
                    fi
                    note "took Pathfinder off AYN's auto launch list"
                    unblocked=1
                    ;;
            esac
            ;;
    esac

    current=$(settings get secure $KEY)
    case "$current" in
        null) current="" ;;
    esac

    case ":$current:" in
        *":$PKG/"*)
            # Switched on: nothing to do, which is the usual case. Unless it
            # has just come off AYN's list, where Android refused it and won't
            # try again until the switch changes: then off, and on again.
            if [ $unblocked = 1 ]; then
                others=$(without "$current" ':' "$PKG/*")
                settings put secure $KEY "$others"
                sleep 1
                if [ -z "$others" ]; then
                    settings put secure $KEY "$SERVICE"
                else
                    settings put secure $KEY "$others:$SERVICE"
                fi
                note "switched it off and on so Android starts it"
            fi
            continue
            ;;
    esac

    # Someone may be switching it off on purpose.
    case "$(in_front)" in
        *com.android.settings*) note "switched off while Settings was open, leaving it alone"; continue ;;
    esac

    # Put Pathfinder back, keeping every other service exactly as it was.
    if [ -z "$current" ]; then
        settings put secure $KEY "$SERVICE"
    else
        settings put secure $KEY "$current:$SERVICE"
    fi
    settings put secure accessibility_enabled 1
    note "switched it back on"
done

note "watchdog stopped"
