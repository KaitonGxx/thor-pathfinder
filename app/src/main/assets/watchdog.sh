#!/system/bin/sh
#
# Thor Pathfinder's watchdog.
#
# Pathfinder starts this through Shizuku, so it runs as the shell user and
# belongs to Shizuku rather than to Pathfinder. That is the whole point: when
# something force-stops or kills Pathfinder, this keeps running and switches
# its accessibility service back on.
#
# It is opt-in, it only ever adds Pathfinder's own component to the list, and
# it stops the moment the marker file is gone. Everything it does is here.
#
# Started with:  setsid sh watchdog.sh <seconds> &
# Stopped with:  rm <marker>

MARK=/data/local/tmp/thorpathfinder-watchdog.on
LOG=/data/local/tmp/thorpathfinder-watchdog.log
SERVICE=com.thorpathfinder.app/com.thorpathfinder.app.PathfinderService
KEY=enabled_accessibility_services
EVERY=${1:-5}

note() {
    echo "$(date '+%m-%d %H:%M:%S')  $1" >> "$LOG"
    # Keep the log short enough to paste into a bug report.
    tail -n 40 "$LOG" > "$LOG.tmp" 2>/dev/null && mv "$LOG.tmp" "$LOG"
}

note "watchdog started, checking every ${EVERY}s"

while [ -f "$MARK" ]; do
    sleep $EVERY
    [ -f "$MARK" ] || break

    current=$(settings get secure $KEY)
    case "$current" in
        null) current="" ;;
    esac

    # Already there: nothing to do, which is the usual case.
    case ":$current:" in
        *":com.thorpathfinder.app/"*) continue ;;
    esac

    # Someone may be switching it off on purpose. If Android's own settings
    # are in front, leave it alone: a watchdog that fights the user is a bug.
    top=$(dumpsys activity activities 2>/dev/null | grep -m1 'ResumedActivity')
    case "$top" in
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
