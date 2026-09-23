#!/bin/bash
#
# Checks the deployed backend against the two shipped sync specs:
#   Requirements/backend-delta-sync-api.md
#   Requirements/backend-active-timer-api.md
#
# Why this exists: every client slice of the cross-device timer was written against those specs
# before the backend shipped, and a contract mismatch here fails *silently*. A flat delta row
# without parentProjectId is dropped without an error, because the column is NOT NULL locally and
# backs the cascade onto projects — the symptom is missing tasks and intervals, not a crash.
#
# Usage:  ./scripts/verify_sync_contract.sh
#
# Credentials come from local.properties (gitignored): BASE_URL, TEST_EMAIL, TEST_PASSWORD.
# It creates a throwaway project per group and deletes it again; the account is left as found.
#
# Exits non-zero if any assertion fails.
#
# bash, not zsh, and on purpose: zsh's `echo` expands backslash escapes, so `echo "$json"` turns a
# \n inside a JSON string literal into a real newline and corrupts the payload. It looks exactly
# like a malformed server response. Responses go to files here rather than through variables.

set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LP="$ROOT/local.properties"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

command -v jq >/dev/null || { echo "jq is required"; exit 2; }
[ -f "$LP" ] || { echo "local.properties not found at $LP"; exit 2; }

BASE_URL=$(grep '^BASE_URL=' "$LP" | cut -d= -f2-)
TEST_EMAIL=$(grep '^TEST_EMAIL=' "$LP" | cut -d= -f2-)
TEST_PASSWORD=$(grep '^TEST_PASSWORD=' "$LP" | cut -d= -f2-)
[ -n "$BASE_URL" ] || { echo "BASE_URL missing from local.properties"; exit 2; }

PASS=0; FAIL=0
ok()  { printf '  PASS  %s\n' "$1"; PASS=$((PASS+1)); }
no()  { printf '  FAIL  %s\n' "$1"; FAIL=$((FAIL+1)); }
chk() { [ "$2" = "$3" ] && ok "$1 ($2)" || no "$1 — expected $3, got $2"; }
hdr() { printf '\n== %s ==\n' "$1"; }

# Python 3.9 compatible: datetime.UTC only exists from 3.11.
now()  { python3 -c 'import datetime;print(datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3]+"Z")'; }
uuid() { python3 -c 'import uuid;print(uuid.uuid4())'; }

# Response body -> $TMP/r.json, status code on stdout.
req() {
  local method="$1" route="$2" body="${3:-}"
  if [ -n "$body" ]; then
    curl -sS -m 40 -X "$method" "$BASE_URL$route" -H "Authorization: Bearer $TOK" \
      -H 'Content-Type: application/json' -d "$body" -o "$TMP/r.json" -w '%{http_code}'
  else
    curl -sS -m 40 -X "$method" "$BASE_URL$route" -H "Authorization: Bearer $TOK" \
      -o "$TMP/r.json" -w '%{http_code}'
  fi
}

new_project() { # $1 = id, $2 = title
  req POST /api/projects "$(jq -nc --arg i "$1" --arg t "$2" --arg s "$(now)" \
    '{id:$i,title:$t,description:"verify_sync_contract",color:-1,startDateTimeUtc:$s,useLightTextColor:false}')"
}
new_task() { # $1 = projectId, $2 = taskId
  req POST "/api/projects/$1/tasks" "$(jq -nc --arg i "$2" --arg s "$(now)" \
    '{id:$i,title:"probe task",durationMillis:0,startDateTimeUtc:$s,endDateTimeUtc:null,isFinished:false,isTimerRunning:false}')"
}
new_subtask() { # $1 = projectId, $2 = taskId, $3 = subTaskId
  req POST "/api/projects/$1/tasks/$2/subtasks" "$(jq -nc --arg i "$3" --arg s "$(now)" \
    '{id:$i,title:"probe subtask",durationMillis:0,startDateTimeUtc:$s,endDateTimeUtc:null,isFinished:false,isTimerRunning:false}')"
}
start_task_timer() { # $1 = intervalId, $2 = taskId, $3 = deviceId, $4 = startedAt
  req PUT /api/timer/active "$(jq -nc --arg i "$1" --arg t "$2" --arg d "$3" --arg s "$4" \
    '{intervalId:$i,kind:"task",parentTaskId:$t,parentSubTaskId:null,parentTaskIntervalId:null,startedAtUtc:$s,deviceId:$d}')"
}
start_subtask_timer() { # $1 = subIntervalId, $2 = taskId, $3 = subTaskId, $4 = taskIntervalId, $5 = deviceId
  req PUT /api/timer/active "$(jq -nc --arg i "$1" --arg t "$2" --arg st "$3" --arg ti "$4" --arg d "$5" --arg s "$(now)" \
    '{intervalId:$i,kind:"sub_task",parentTaskId:$t,parentSubTaskId:$st,parentTaskIntervalId:$ti,startedAtUtc:$s,deviceId:$d}')"
}
stop_timer() { # $1 = intervalId, $2 = endedAt
  req POST /api/timer/active/stop "$(jq -nc --arg i "$1" --arg e "$2" '{intervalId:$i,endedAtUtc:$e}')"
}

printf 'Verifying %s\n' "$BASE_URL"
RC=$(curl -sS -m 30 -X POST "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg e "$TEST_EMAIL" --arg p "$TEST_PASSWORD" '{email:$e,password:$p}')" \
  -o "$TMP/login.json" -w '%{http_code}')
[ "$RC" = 200 ] || { echo "login failed (HTTP $RC)"; exit 2; }
TOK=$(jq -r .accessToken "$TMP/login.json")

# ---------------------------------------------------------------- A. the delta change feed
hdr "A. GET /api/sync/changes"
req GET /api/sync/changes >/dev/null
cp "$TMP/r.json" "$TMP/delta.json"

# The highest-value check in the file: the client drops a flat row without parentProjectId, and
# the symptom is missing tasks and intervals rather than an error.
MISSING=$(jq '[(.tasks[],.taskIntervals[],.subTasks[],.subTaskIntervals[])
               | select((.parentProjectId // null) == null)] | length' "$TMP/delta.json")
TOTAL=$(jq '[(.tasks[],.taskIntervals[],.subTasks[],.subTaskIntervals[])] | length' "$TMP/delta.json")
chk "parentProjectId on all $TOTAL flat rows" "$MISSING" 0
chk "serverNowUtc present" "$(jq -r 'if .serverNowUtc then "yes" else "no" end' "$TMP/delta.json")" yes
chk "startedByDeviceId on interval rows" \
  "$(jq -r 'if ([(.taskIntervals[],.subTaskIntervals[])]|length)==0 then "n/a"
            elif all((.taskIntervals[],.subTaskIntervals[]); has("startedByDeviceId")) then "yes" else "no" end' "$TMP/delta.json")" yes

CUR=$(jq -r .cursor "$TMP/delta.json")
req GET "/api/sync/changes?since=$CUR" >/dev/null
# Rows only, not tombstones. A since-less pull deliberately returns no tombstones — a fresh client
# has nothing to delete — so its cursor is the highest seq among the rows it *did* include, and
# tombstones written earlier legitimately sit above it.
chk "cursor is the highest included, not the server max" \
  "$(jq '[.projects,.tasks,.taskIntervals,.subTasks,.subTaskIntervals]|map(length)|add' "$TMP/r.json")" 0
chk "a since-less pull carries no tombstones" "$(jq '.tombstones|length' "$TMP/delta.json")" 0
req GET "/api/sync/changes?since=999999999" >/dev/null
chk "a since newer than anything echoes back" "$(jq -r .cursor "$TMP/r.json")" 999999999
chk "a negative since is rejected" "$(req GET '/api/sync/changes?since=-5')" 400

# ---------------------------------------------------------------- B. paging
hdr "B. paging at limit=2"
SINCE=""; PAGE=0; PREV=-1; WALK=ok
: > "$TMP/paged.txt"
while :; do
  Q="?limit=2"; [ -n "$SINCE" ] && Q="$Q&since=$SINCE"
  req GET "/api/sync/changes$Q" >/dev/null
  jq -r '[(.projects[],.tasks[],.taskIntervals[],.subTasks[],.subTaskIntervals[])|.id]|.[]' \
    "$TMP/r.json" >> "$TMP/paged.txt" || { WALK="unparseable page at since=$SINCE"; break; }
  C=$(jq -r .cursor "$TMP/r.json"); MORE=$(jq -r .hasMore "$TMP/r.json")
  PAGE=$((PAGE+1))
  if [ "$C" -le "$PREV" ]; then WALK="cursor did not advance ($PREV -> $C)"; break; fi
  PREV=$C; SINCE=$C
  [ "$MORE" = "false" ] && break
  [ "$PAGE" -gt 1000 ] && { WALK="runaway paging"; break; }
done
chk "the walk terminates with an advancing cursor" "$WALK" ok
jq -r '[(.projects[],.tasks[],.taskIntervals[],.subTasks[],.subTaskIntervals[])|.id]|.[]' \
  "$TMP/delta.json" | sort > "$TMP/unpaged.txt"
sort "$TMP/paged.txt" > "$TMP/paged_sorted.txt"
chk "no row is split across pages (duplicates)" "$(uniq -d "$TMP/paged_sorted.txt" | wc -l | tr -d ' ')" 0
chk "no row is lost across pages" "$(comm -23 "$TMP/unpaged.txt" "$TMP/paged_sorted.txt" | wc -l | tr -d ' ')" 0
printf '        (%s pages, %s rows)\n' "$PAGE" "$(wc -l < "$TMP/paged_sorted.txt" | tr -d ' ')"

# ---------------------------------------------------------------- C. the active timer
hdr "C. /api/timer/active — task timers"
P=$(uuid); T=$(uuid); DEV=$(uuid); A=$(uuid); B=$(uuid)
chk "setup: create project" "$(new_project "$P" 'ZZ verify_sync_contract')" 201
chk "setup: create task" "$(new_task "$P" "$T")" 201

chk "GET with nothing running is 204" "$(req GET /api/timer/active)" 204
[ -s "$TMP/r.json" ] && no "the 204 carried a body" || ok "the 204 has no body"

SA=$(now)
chk "PUT start" "$(start_task_timer "$A" "$T" "$DEV" "$SA")" 200
cp "$TMP/r.json" "$TMP/startA.json"
chk "the response carries active, touched and serverNowUtc" \
  "$(jq -r 'if (.active and (.touched|type=="array") and .serverNowUtc) then "yes" else "no" end' "$TMP/startA.json")" yes
chk "touched includes the new interval" \
  "$(jq -r --arg i "$A" 'if ([.touched[].id]|index($i)) != null then "yes" else "no" end' "$TMP/startA.json")" yes
chk "the device id is echoed back" "$(jq -r '.active.startedByDeviceId' "$TMP/startA.json")" "$DEV"

chk "replaying the same start is 200" "$(start_task_timer "$A" "$T" "$DEV" "$(now)")" 200
chk "the replay leaves startedAtUtc alone" "$(jq -r '.active.startedAtUtc' "$TMP/r.json")" "$SA"

sleep 1; SB=$(now)
chk "starting B supersedes A" "$(start_task_timer "$B" "$T" "$DEV" "$SB")" 200
cp "$TMP/r.json" "$TMP/startB.json"
chk "A is closed at B's startedAtUtc" \
  "$(jq -r --arg i "$A" '.touched[]|select(.id==$i)|.endDateTimeUtc' "$TMP/startB.json")" "$SB"
chk "touched carries both intervals" \
  "$(jq -r --arg a "$A" --arg b "$B" 'if (([.touched[].id]|index($a)) != null and ([.touched[].id]|index($b)) != null) then "yes" else "no" end' "$TMP/startB.json")" yes

chk "stopping a non-active interval is 409" "$(stop_timer "$A" "$(now)")" 409
chk "the 409 names the timer that is running" \
  "$(jq -r 'if .active.intervalId then "yes" else "no" end' "$TMP/r.json")" yes
chk "restarting a closed interval is 409" "$(start_task_timer "$A" "$T" "$DEV" "$(now)")" 409

EB=$(now)
chk "stopping the active interval" "$(stop_timer "$B" "$EB")" 200
D1=$(jq -r --arg i "$B" '.touched[]|select(.id==$i)|.durationMillis' "$TMP/r.json")
chk "replaying an identical stop is 200" "$(stop_timer "$B" "$EB")" 200
chk "the replay touches nothing" "$(jq -r '.touched|length' "$TMP/r.json")" 0
req GET "/api/sync/changes?since=0" >/dev/null
chk "the stored duration is unchanged by the replay" \
  "$(jq -r --arg i "$B" '.taskIntervals[]|select(.id==$i)|.durationMillis' "$TMP/r.json")" "$D1"
chk "the device id survived the stop" \
  "$(jq -r --arg i "$A" '.taskIntervals[]|select(.id==$i)|.startedByDeviceId' "$TMP/r.json")" "$DEV"
chk "nothing is running again" "$(req GET /api/timer/active)" 204

C=$(uuid)
start_task_timer "$C" "$T" "$DEV" "$(now)" >/dev/null
chk "an end before the start is rejected" "$(stop_timer "$C" '2020-01-01T00:00:00.000Z')" 400
stop_timer "$C" "$(now)" >/dev/null

# ---------------------------------------------------------------- D. nested subtask timers
hdr "D. /api/timer/active — subtask timers"
ST=$(uuid); TI=$(uuid); STI=$(uuid)
chk "setup: create subtask" "$(new_subtask "$P" "$T" "$ST")" 201

chk "a subtask start opens its enclosing task interval" "$(start_subtask_timer "$STI" "$T" "$ST" "$TI" "$DEV")" 200
cp "$TMP/r.json" "$TMP/sub1.json"
chk "the enclosing interval uses the id the client named" \
  "$(jq -r --arg i "$TI" 'if ([.touched[]|select(.kind=="task")|.id]|index($i)) != null then "yes" else "no" end' "$TMP/sub1.json")" yes
chk "both intervals start at the same instant" \
  "$(jq -r 'if ([.touched[].startDateTimeUtc]|unique|length) == 1 then "yes" else "no" end' "$TMP/sub1.json")" yes
chk "the subtask is what is running" "$(jq -r '.active.kind' "$TMP/sub1.json")" sub_task

chk "stopping a subtask that opened its parent closes both" "$(stop_timer "$STI" "$(now)")" 200
chk "  both closed at the same instant" \
  "$(jq -r 'if ([.touched[].endDateTimeUtc]|unique|length) == 1 then "yes" else "no" end' "$TMP/r.json")" yes

# The known-failing one. See Requirements/backend-active-timer-api.md section 3.
TI2=$(uuid); STI2=$(uuid)
start_task_timer "$TI2" "$T" "$DEV" "$(now)" >/dev/null
sleep 1
start_subtask_timer "$STI2" "$T" "$ST" "$TI2" "$DEV" >/dev/null
sleep 1
stop_timer "$STI2" "$(now)" >/dev/null
chk "a task timer started independently survives its subtask being stopped" \
  "$(req GET /api/timer/active)" 200
chk "  and it is the task interval that is still running" \
  "$(jq -r '.intervalId // "none"' "$TMP/r.json")" "$TI2"
stop_timer "$TI2" "$(now)" >/dev/null

# ---------------------------------------------------------------- E. cascaded tombstones
hdr "E. tombstones"
req GET "/api/sync/changes?since=0" >/dev/null
CUR=$(jq -r .cursor "$TMP/r.json")
RCDEL=$(req DELETE "/api/projects/$P")
chk "delete the probe project" "$([ "$RCDEL" -lt 300 ] && echo ok || echo "$RCDEL")" ok
req GET "/api/sync/changes?since=$CUR" >/dev/null
# The entityType is checked, not just the id. Matching on the id alone is what let a real bug
# through: the client groups tombstones by this exact string to decide which table to delete from,
# it was using the outbox's local table names, and those disagree with the wire on `task` and
# `sub_task`. Every task and subtask deleted on one device stayed on the others, and this script
# said the server was fully compliant throughout — which it was. See Tombstone's companion.
for triple in "project:$P:project" "task:$T:task" "subtask:$ST:sub_task" \
              "interval:$A:task_interval" "subtask interval:$STI:sub_task_interval"; do
  label="${triple%%:*}"; rest="${triple#*:}"; id="${rest%%:*}"; want="${rest##*:}"
  chk "tombstone for the $label" \
    "$(jq -r --arg i "$id" 'if ([.tombstones[].entityId]|index($i)) != null then "yes" else "no" end' "$TMP/r.json")" yes
  chk "  and the client will read it as \"$want\"" \
    "$(jq -r --arg i "$id" '[.tombstones[]|select(.entityId==$i)|.entityType]|first // "missing"' "$TMP/r.json")" "$want"
done

printf '\n==== %s passed, %s failed ====\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
  printf 'Two of these are expected until the backend is fixed: a task timer started\n'
  printf 'independently must survive its subtask being stopped, and it must still be what is\n'
  printf 'running afterwards. See backend-active-timer-api.md section 3.\n'
fi
[ "$FAIL" -eq 0 ]
