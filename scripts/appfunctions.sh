#!/usr/bin/env bash
# Tries The Last Hit's AppFunctions on a phone over adb, the way Gemini would call them.
# Needs Android 16 or newer and the app installed (./gradlew :app:installDebug), with set-up done.
# docs/APPFUNCTIONS.md walks through it.
#
#   scripts/appfunctions.sh check              the phone has AppFunctions, and has indexed all five
#   scripts/appfunctions.sh list               every app's functions, as the phone indexed them (JSON)
#   scripts/appfunctions.sh products           listNrtProducts
#   scripts/appfunctions.sh summary            getNrtSummary
#   scripts/appfunctions.sh log DESCRIPTION [--qty N] [--tags a,b] [--at 2026-10-23T15:00]
#                                   [--strength 1-5] [--keep]
#                                              logNrtUsage, such as: log "two 4 mg gums" --tags stressed
#   scripts/appfunctions.sh undo               undoLastNrtEntry
#   scripts/appfunctions.sh advice [TAG]       getNrtAdvice, for a tag or the top trigger
#   scripts/appfunctions.sh run FUNCTION JSON  any of the five with parameters as JSON
#   scripts/appfunctions.sh tour               each function once, then offers to undo the test dose
#   scripts/appfunctions.sh help               the phone's own help for cmd app_function
#
# ADB picks another adb, ANDROID_SERIAL picks a phone, and BRIEF=1 asks for short YAML instead of JSON.
set -euo pipefail

PACKAGE=app.thelasthit.nrt
PREFIX=app.thelasthit.nrt.agents.NrtAppFunctions
FUNCTIONS=(logNrtUsage listNrtProducts getNrtSummary undoLastNrtEntry getNrtAdvice)
ADB=${ADB:-adb}

die() {
    echo "$*" >&2
    exit 1
}

# Wraps text in single quotes for the phone's shell, closing and reopening around any single quote.
shell_quote() {
    local text=${1//\'/\'\\\'\'}
    printf "'%s'" "$text"
}

# Text as a JSON string, with backslashes and double quotes escaped.
json_string() {
    local text=${1//\\/\\\\}
    text=${text//\"/\\\"}
    printf '"%s"' "$text"
}

# Calls one of the five with JSON parameters and prints what comes back.
call() {
    local function=$1 parameters=${2:-"{}"}
    local brief=""
    if [ "${BRIEF:-}" = 1 ]; then brief=" --brief-yaml"; fi
    echo "> $function $parameters" >&2
    "$ADB" shell "cmd app_function execute-app-function --package $PACKAGE --function $(shell_quote "$PREFIX#$function") --parameters $(shell_quote "$parameters")$brief"
    echo
}

check() {
    local help
    help=$("$ADB" shell cmd app_function help 2>&1) || true
    case $help in
        *"Can't find service"*) die "This phone has no AppFunctions. They need Android 16 or newer." ;;
    esac
    local listed missing=0
    listed=$("$ADB" shell cmd app_function list-app-functions)
    for function in "${FUNCTIONS[@]}"; do
        if printf '%s' "$listed" | grep -q "$PREFIX#$function"; then
            echo "indexed  $function"
        else
            echo "missing  $function"
            missing=1
        fi
    done
    if [ "$missing" = 1 ]; then
        die "Install a fresh debug build and give the phone a minute to index it. docs/APPFUNCTIONS.md has more."
    fi
}

log() {
    [ $# -ge 1 ] || die "log needs a description, such as: log \"2 mg gum\""
    local json
    json="{\"productDescription\": $(json_string "$1")"
    shift
    while [ $# -gt 0 ]; do
        case $1 in
            --qty) json+=", \"quantity\": $2"; shift 2 ;;
            --strength) json+=", \"cravingStrength\": $2"; shift 2 ;;
            --keep) json+=", \"keepDuplicate\": true"; shift ;;
            --tags)
                local tags="" tag
                IFS=, read -r -a list <<< "$2"
                for tag in "${list[@]}"; do tags+="${tags:+, }$(json_string "$tag")"; done
                json+=", \"tags\": [$tags]"
                shift 2
                ;;
            --at)
                # A LocalDateTime goes as its parts, the way the AppFunctions library sends one.
                [[ $2 =~ ^([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2})$ ]] || die "--at takes a time like 2026-10-23T15:00"
                json+=", \"time\": {\"year\": $((10#${BASH_REMATCH[1]})), \"month\": $((10#${BASH_REMATCH[2]}))"
                json+=", \"dayOfMonth\": $((10#${BASH_REMATCH[3]})), \"hour\": $((10#${BASH_REMATCH[4]}))"
                json+=", \"minute\": $((10#${BASH_REMATCH[5]})), \"second\": 0, \"nanoOfSecond\": 0}"
                shift 2
                ;;
            *) die "log doesn't take $1" ;;
        esac
    done
    call logNrtUsage "$json}"
}

tour() {
    check
    call listNrtProducts
    call getNrtSummary
    log "2 mg gum" --tags stressed
    call getNrtAdvice "{\"tag\": \"Stressed\"}"
    call getNrtAdvice
    echo "The tour logged a real dose of 2 mg gum, unless it asked about a duplicate or a product."
    read -r -p "Undo the dose logged last now? [y/N] " answer
    case $answer in
        y | Y) call undoLastNrtEntry ;;
        *) echo "Left as it is. Undo it in the app's History, or with: scripts/appfunctions.sh undo" ;;
    esac
}

command=${1:-help}
[ $# -gt 0 ] && shift
case $command in
    check) check ;;
    list) "$ADB" shell cmd app_function list-app-functions ;;
    products) call listNrtProducts ;;
    summary) call getNrtSummary ;;
    log) log "$@" ;;
    undo) call undoLastNrtEntry ;;
    advice)
        if [ $# -ge 1 ]; then call getNrtAdvice "{\"tag\": $(json_string "$1")}"; else call getNrtAdvice; fi
        ;;
    run)
        [ $# -ge 1 ] || die "run needs a function, such as: run getNrtSummary '{}'"
        call "$1" "${2:-"{}"}"
        ;;
    tour) tour ;;
    help) "$ADB" shell cmd app_function help ;;
    *) die "Unknown command $command. See the top of $0." ;;
esac
