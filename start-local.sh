#!/usr/bin/env bash
set -e

mvn -q package

java -cp target/classes dfs.StorageNode --id n1 --port 7001 --dir data/n1 --coordinator &
PID_N1=$!

java -cp target/classes dfs.StorageNode --id n2 --port 7002 --dir data/n2 &
PID_N2=$!

java -cp target/classes dfs.StorageNode --id n3 --port 7003 --dir data/n3 &
PID_N3=$!

trap "kill $PID_N1 $PID_N2 $PID_N3 2>/dev/null || true" EXIT INT TERM

wait
