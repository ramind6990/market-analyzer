#!/usr/bin/env bash
# Builds market-data-aggregator and (re)deploys CandleAggregationJob to the Flink cluster of
# docker-compose-messaging.yaml.
#
#   1. mvn package of the aggregator module (shaded jar, compiled for Java 21 like the Flink image)
#   2. copy the jar to data/flink/target (mounted read-only at /opt/flink/usrlib in the jobmanager)
#   3. if the job is already running: stop it with a savepoint (keeps open windows / Kafka offsets)
#   4. flink run (detached), resuming from that savepoint when there is one
#
# Usage: .infra/deploy-flink-aggregator.sh [--no-build] [--fresh]
#   --no-build  skip the Maven build, deploy the jar already in target/
#   --fresh     stop a running job without savepoint and start without state (Kafka offsets from the group)
#
# Overridable: JOBMANAGER (container), KAFKA_BOOTSTRAP (as seen from inside the compose network), JOB_ARGS (extra args).
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$INFRA_DIR/.." && pwd)"
MODULE="market-data/market-data-aggregator"
JOB_NAME="candle-aggregation"
MAIN_CLASS="com.rmrx.marketanalyzer.marketdata.aggregator.CandleAggregationJob"
JAR_NAME="market-data-aggregator.jar"
HOST_TARGET_DIR="$INFRA_DIR/data/flink/target"   # bind-mounted to /opt/flink/usrlib
CONTAINER_JAR="/opt/flink/usrlib/$JAR_NAME"
SAVEPOINT_DIR="file:///opt/flink/savepoints"
JOBMANAGER="${JOBMANAGER:-flink_jobmanager}"
KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-market-analyzer-kafka:9092}"
JOB_ARGS="${JOB_ARGS:-}"

BUILD=true
FRESH=false
for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=false ;;
    --fresh) FRESH=true ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

# Git Bash on Windows rewrites arguments starting with "/" into Windows paths; container paths must stay as they are.
export MSYS_NO_PATHCONV=1

flink() {
  docker exec "$JOBMANAGER" flink "$@"
}

if ! docker ps --format '{{.Names}}' | grep -qx "$JOBMANAGER"; then
  echo "Flink jobmanager container '$JOBMANAGER' is not running (docker compose -f .infra/docker-compose-messaging.yaml up -d)" >&2
  exit 1
fi

# ---- 1. build
if $BUILD; then
  echo ">> building $MODULE"
  (cd "$ROOT_DIR" && mvn -q -pl "$MODULE" -am package -DskipTests)
fi
SHADED_JAR="$(ls "$ROOT_DIR/$MODULE"/target/*-shaded.jar 2>/dev/null | head -n 1 || true)"
if [[ -z "$SHADED_JAR" ]]; then
  echo "no shaded jar in $ROOT_DIR/$MODULE/target (build first)" >&2
  exit 1
fi

# ---- 2. copy
mkdir -p "$HOST_TARGET_DIR"
cp "$SHADED_JAR" "$HOST_TARGET_DIR/$JAR_NAME"
echo ">> copied $(basename "$SHADED_JAR") -> $HOST_TARGET_DIR/$JAR_NAME"

# ---- 3. stop a running job
SAVEPOINT=""
JOB_ID="$(flink list -r 2>/dev/null | grep " : $JOB_NAME (" | awk -F ' : ' '{print $2}' | head -n 1 || true)"
if [[ -n "$JOB_ID" ]]; then
  if $FRESH; then
    echo ">> cancelling running job $JOB_ID (no savepoint)"
    flink cancel "$JOB_ID"
  else
    echo ">> stopping running job $JOB_ID with savepoint"
    STOP_OUTPUT="$(flink stop --savepointPath "$SAVEPOINT_DIR" "$JOB_ID")"
    echo "$STOP_OUTPUT"
    SAVEPOINT="$(echo "$STOP_OUTPUT" | sed -n 's/.*Savepoint completed\. Path: \(.*\)$/\1/p' | tail -n 1)"
    if [[ -z "$SAVEPOINT" ]]; then
      echo "could not read the savepoint path; rerun with --fresh to start without state" >&2
      exit 1
    fi
  fi
else
  echo ">> no running '$JOB_NAME' job"
fi

# ---- 4. run
RUN_ARGS=(run -d -c "$MAIN_CLASS")
if [[ -n "$SAVEPOINT" ]]; then
  echo ">> resuming from $SAVEPOINT"
  RUN_ARGS+=(-s "$SAVEPOINT")
fi
RUN_ARGS+=("$CONTAINER_JAR" --kafka.bootstrap-servers "$KAFKA_BOOTSTRAP")
# shellcheck disable=SC2206
[[ -n "$JOB_ARGS" ]] && RUN_ARGS+=($JOB_ARGS)

echo ">> flink ${RUN_ARGS[*]}"
flink "${RUN_ARGS[@]}"
echo ">> running jobs:"
flink list -r
