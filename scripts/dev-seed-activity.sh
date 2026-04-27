#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:3030}"
PASSWORD="${PASSWORD:-password123}"
LOOP_MODE="${LOOP_MODE:-0}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-5}"
ITERATIONS="${ITERATIONS:-0}"

log() {
  printf '[dev-seed] %s\n' "$*"
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/dev-seed-activity.sh
  LOOP_MODE=1 INTERVAL_SECONDS=10 ./scripts/dev-seed-activity.sh
  LOOP_MODE=1 ITERATIONS=5 INTERVAL_SECONDS=3 ./scripts/dev-seed-activity.sh

Env:
  BASE_URL           target server base url (default: http://localhost:3030)
  PASSWORD           sample user password (default: password123)
  LOOP_MODE          1 to keep sending activity, 0 for one-shot (default: 0)
  INTERVAL_SECONDS   sleep between batches in loop mode (default: 5)
  ITERATIONS         0 for infinite, or stop after N batches (default: 0)
EOF
}

require_server() {
  local status
  status="$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/api/v1/timeline?limit=1" || true)"
  if [[ -z "${status}" || "${status}" == "000" ]]; then
    log "server is not reachable at ${BASE_URL}"
    log "start it with: lein run server"
    exit 1
  fi
}

extract_json_field() {
  local key="$1"
  local value
  value="$(
    grep -o "\"${key}\":\"[^\"]*\"" | head -n1 | cut -d'"' -f4 || true
  )"
  printf '%s' "${value}"
}

auth_header() {
  local token="$1"
  printf 'Authorization: Token %s' "${token}"
}

login() {
  local username="$1"
  local response
  if ! response="$(curl -sS -X POST "${BASE_URL}/api/v1/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${username}\",\"password\":\"${PASSWORD}\"}")"; then
    log "failed to reach login endpoint for ${username}"
    exit 1
  fi
  local token
  token="$(printf '%s' "${response}" | extract_json_field "token")"
  if [[ -z "${token}" ]]; then
    log "failed to login: ${username}"
    printf '%s\n' "${response}"
    exit 1
  fi
  printf '%s' "${token}"
}

create_post() {
  local token="$1"
  local content="$2"
  local response
  response="$(curl -fsS -X POST "${BASE_URL}/api/v1/posts" \
    -H "$(auth_header "${token}")" \
    -H "Content-Type: application/json" \
    -d "{\"content\":\"${content}\"}")"
  local post_id
  post_id="$(printf '%s' "${response}" | extract_json_field "posts/id")"
  if [[ -z "${post_id}" ]]; then
    log "failed to create post"
    printf '%s\n' "${response}"
    exit 1
  fi
  printf '%s' "${post_id}"
}

create_reply() {
  local token="$1"
  local parent_id="$2"
  local content="$3"
  local response
  response="$(curl -fsS -X POST "${BASE_URL}/api/v1/posts" \
    -H "$(auth_header "${token}")" \
    -H "Content-Type: application/json" \
    -d "{\"content\":\"${content}\",\"parent_id\":\"${parent_id}\"}")"
  local post_id
  post_id="$(printf '%s' "${response}" | extract_json_field "posts/id")"
  if [[ -z "${post_id}" ]]; then
    log "failed to create reply"
    printf '%s\n' "${response}"
    exit 1
  fi
  printf '%s' "${post_id}"
}

like_post() {
  local token="$1"
  local post_id="$2"
  curl -fsS -X POST "${BASE_URL}/api/v1/posts/${post_id}/like" \
    -H "$(auth_header "${token}")" >/dev/null
}

run_batch() {
  local suffix="$1"
  local alice_token="$2"
  local bob_token="$3"
  local carol_token="$4"
  local david_token="$5"
  local emma_token="$6"
  local frank_token="$7"
  local grace_token="$8"

  log "creating posts"
  local alice_post bob_post carol_post
  alice_post="$(create_post "${alice_token}" "load-sample後の動作確認用に、Aliceが投稿を作成しました。 batch=${suffix}")"
  bob_post="$(create_post "${bob_token}" "BobがRedis timeline配布の確認用に投稿しています。 batch=${suffix}")"
  carol_post="$(create_post "${carol_token}" "Carolが返信といいねの動線確認用に投稿しました。 batch=${suffix}")"

  log "creating replies"
  create_reply "${emma_token}" "${alice_post}" "EmmaからAliceへの返信です。 batch=${suffix}"
  create_reply "${david_token}" "${bob_post}" "DavidからBobへの返信です。 batch=${suffix}"
  create_reply "${grace_token}" "${carol_post}" "GraceからCarolへの返信です。 batch=${suffix}"

  log "adding likes"
  like_post "${bob_token}" "${alice_post}"
  like_post "${carol_token}" "${alice_post}"
  like_post "${david_token}" "${bob_post}"
  like_post "${emma_token}" "${bob_post}"
  like_post "${frank_token}" "${carol_post}"
  like_post "${grace_token}" "${carol_post}"

  log "done"
  log "alice post: ${alice_post}"
  log "bob post:   ${bob_post}"
  log "carol post: ${carol_post}"
}

main() {
  if [[ "${1:-}" == "--help" ]]; then
    usage
    exit 0
  fi

  require_server

  log "logging in sample users"
  local alice_token bob_token carol_token david_token emma_token frank_token grace_token
  alice_token="$(login alice)"
  bob_token="$(login bob)"
  carol_token="$(login carol)"
  david_token="$(login david)"
  emma_token="$(login emma)"
  frank_token="$(login frank)"
  grace_token="$(login grace)"
  log "login completed"
  log "alice token: ${alice_token}"
  log "bob token: ${bob_token}"
  log "carol token: ${carol_token}"
  log "david token: ${david_token}"
  log "emma token: ${emma_token}"
  log "frank token: ${frank_token}"
  log "grace token: ${grace_token}"

  local iteration=1
  while true; do
    run_batch "${iteration}" \
      "${alice_token}" \
      "${bob_token}" \
      "${carol_token}" \
      "${david_token}" \
      "${emma_token}" \
      "${frank_token}" \
      "${grace_token}"

    if [[ "${LOOP_MODE}" != "1" ]]; then
      break
    fi

    if [[ "${ITERATIONS}" != "0" && "${iteration}" -ge "${ITERATIONS}" ]]; then
      log "reached iterations=${ITERATIONS}, stopping"
      break
    fi

    iteration=$((iteration + 1))
    log "sleeping ${INTERVAL_SECONDS}s before next batch"
    sleep "${INTERVAL_SECONDS}"
  done
}

main "$@"
