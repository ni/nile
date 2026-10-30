#!/bin/bash
set -euo pipefail

usage() {
	cat >&2 <<EOF

Syntax:
$(basename $BASH_SOURCE) kas_target_yml export_dir
   Copy the artifacts that kas_target_yml declares, and the logs of tasks
   that warned or failed, from the last build into
   export_dir/<target name>.

EOF
	exit 2
}

[ $# -eq 2 ] || usage

spec=$1
dest="$2/$(basename "${spec%.*}")"
root_dir=$(cd "$(dirname "$BASH_SOURCE")/../.." && pwd)
build_dir="${KAS_BUILD_DIR:-$root_dir/build}"

mkdir -p "$dest"
cd "$root_dir"

./kas-container dump --format json "$spec" |
	python3 -c '
import json, sys
for name, path in json.load(sys.stdin).get("artifacts", {}).items():
    print(f"{name}\t{path}")' |
	while IFS=$'\t' read -r name path; do
		if [ -e "$build_dir/$path" ]; then
			mkdir -p "$(dirname "$dest/$name")"
			cp -rL "$build_dir/$path" "$dest/$name"
		else
			echo "WARNING: $spec: artifact $name is missing ($path)" >&2
		fi
	done

# Dereference symlinks, which the export share can't store.
mkdir -p "$dest/logs"
if [ -d "$build_dir/tmp/log/cooker" ]; then
	cp -rL "$build_dir/tmp/log/cooker" "$dest/logs/"
fi

# The console shows bbwarn messages but not the rest of the task's output.
if [ -d "$build_dir/tmp/work" ]; then
	find "$build_dir/tmp/work" -path '*/temp/log.do_*' -type l -print0 |
		xargs -0 -r grep -lsE '^(WARNING|ERROR):' |
		while read -r log; do
			rel=${log#$build_dir/tmp/work/}
			mkdir -p "$dest/logs/work/$(dirname "$rel")"
			cp -L "$log" "$dest/logs/work/$rel"
		done || true
fi
