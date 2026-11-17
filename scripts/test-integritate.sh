#!/usr/bin/env bash
set -euo pipefail

ECHIPA="${1:-1}"
TMP_DIR="$(mktemp -d)"
if command -v cygpath >/dev/null 2>&1; then
    TMP_DIR="$(cygpath -m "$TMP_DIR")"
fi
SRC_DIR="$TMP_DIR/src"
OUT_DIR="$TMP_DIR/out"
NODE_DIR="$TMP_DIR/nodes"
CMD_FILE="$TMP_DIR/commands.txt"

mkdir -p "$SRC_DIR" "$OUT_DIR" "$NODE_DIR/n1" "$NODE_DIR/n2" "$NODE_DIR/n3"

PID_N1=""
PID_N2=""
PID_N3=""

cleanup() {
    for pid in "$PID_N1" "$PID_N2" "$PID_N3"; do
        if [[ -n "$pid" ]]; then
            kill "$pid" 2>/dev/null || true
            wait "$pid" 2>/dev/null || true
        fi
    done
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT INT TERM

if [[ ! -d "target/classes" ]]; then
    mvn -q package
fi

java -cp target/classes dfs.StorageNode --id n1 --port 7001 --dir "$NODE_DIR/n1" --echipa "$ECHIPA" --coordinator >"$TMP_DIR/n1.log" 2>&1 &
PID_N1=$!
java -cp target/classes dfs.StorageNode --id n2 --port 7002 --dir "$NODE_DIR/n2" --echipa "$ECHIPA" >"$TMP_DIR/n2.log" 2>&1 &
PID_N2=$!
java -cp target/classes dfs.StorageNode --id n3 --port 7003 --dir "$NODE_DIR/n3" --echipa "$ECHIPA" >"$TMP_DIR/n3.log" 2>&1 &
PID_N3=$!

sleep 3

NODES=(n1 n2 n3)
: > "$CMD_FILE"
echo "nodes" >> "$CMD_FILE"

for i in $(seq 1 100); do
    size=$((512 + i * 2048))
    fname="fisier_${i}.bin"
    head -c "$size" /dev/urandom > "$SRC_DIR/$fname"
    node="${NODES[$(((i - 1) % 3))]}"
    echo "put $node $SRC_DIR/$fname" >> "$CMD_FILE"
done

for i in $(seq 1 100); do
    fname="fisier_${i}.bin"
    node="${NODES[$(((i - 1) % 3))]}"
    echo "get $node $fname $OUT_DIR" >> "$CMD_FILE"
done

echo "exit" >> "$CMD_FILE"

java -cp target/classes dfs.Client --echipa "$ECHIPA" < "$CMD_FILE" > "$TMP_DIR/client.log"

ok=0
fail=0
for i in $(seq 1 100); do
    fname="fisier_${i}.bin"
    if [[ -f "$OUT_DIR/$fname" ]] && cmp -s "$SRC_DIR/$fname" "$OUT_DIR/$fname"; then
        ok=$((ok + 1))
    else
        echo "Diferență la $fname"
        fail=$((fail + 1))
    fi
done

echo "Verificate 100 fișiere: $ok corecte, $fail diferite."
if [[ "$fail" -ne 0 ]]; then
    exit 1
fi
