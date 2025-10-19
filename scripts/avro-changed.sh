#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${GITHUB_BASE_REF:-}"
if [[ -n "${BASE_REF}" ]]; then
  git fetch --no-tags --depth=1 origin "${BASE_REF}"
  BASE="$(git merge-base HEAD "origin/${BASE_REF}")"
else
  BASE="${GITHUB_SHA:-HEAD}^"
fi

CHANGED_FILES="$(git diff --name-only "${BASE}" HEAD || true)"

if echo "${CHANGED_FILES}" | grep -E '\.avsc$' >/dev/null 2>&1; then
  echo "changed=true" >> "$GITHUB_OUTPUT"
  echo "Detected Avro schema changes:"
  echo "${CHANGED_FILES}" | grep -E '\.avsc$'
else
  echo "changed=false" >> "$GITHUB_OUTPUT"
  echo "No Avro schema changes detected."
fi
