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
# list. A switch-off made in Android's settings is the user's own choice, and
# it leaves that alone until the service is switched on again. It stops the
# moment the marker file is gone, or when Pathfinder stops it. Everything it
# does is here.
#
# Started with:  setsid sh watchdog.sh <seconds> &
# Stopped with:  rm <marker>, then pkill

MARK=/data/local/tmp/thorpathfinder-watchdog.on
HELD=/data/local/tmp/thorpathfinder-watchdog.held
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

# Stopped by Pathfinder: go at once. The shell holds a signal back until the
# command in front of it ends, so the wait is on a sleep in the background,
# which the signal does interrupt; otherwise a restart would leave the old copy
# running for up to one more interval.
trap 'kill $nap 2>/dev/null; exit 0' TERM

holding=0
note "watchdog started, checking every ${EVERY}s"

while [ -f "$MARK" ]; do
    sleep $EVERY &
    nap=$!
    wait $nap
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
            # Switched on, by whoever: a switch-off made in Settings no longer holds.
            if [ -f "$HELD" ]; then
                rm -f "$HELD"
                note "switched on again, back to watching"
            fi
            holding=0
            # Nothing else to do, which is the usual case. Unless it has just
            # come off AYN's list, where Android refused it and won't try
            # again until the switch changes: then off, and on again.
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

    # Switched off in Android's settings: the user's own choice. Pathfinder
    # leaves the marker the moment its service stops there, and a switch-off
    # seen here while Settings is still open counts too. Either way it stays
    # off, after Settings closes and after a restart, until it is switched on.
    if [ ! -f "$HELD" ]; then
        case "$(in_front)" in
            *com.android.settings*) touch "$HELD" ;;
        esac
    fi
    if [ -f "$HELD" ]; then
        [ $holding = 1 ] || note "switched off in Settings, leaving it off until it is switched on again"
        holding=1
        continue
    fi

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
