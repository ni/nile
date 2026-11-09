#!/bin/bash
set -euo pipefail

usage() {
	cat >&2 <<EOF

Syntax:
$(basename "$BASH_SOURCE") kas_target_yml export_dir
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
build_dir="${KAS_BUILD_DIR:-${KAS_WORK_DIR:-$root_dir}/build}"
tmp_dir="$build_dir/tmp"

mkdir -p "$dest"
cd "$root_dir"

# Copy with cp -L throughout: the export share can't store symlinks.
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

mkdir -p "$dest/logs"
if [ -d "$tmp_dir/log/cooker" ]; then
	cp -rL "$tmp_dir/log/cooker" "$dest/logs/"
fi

# The console shows bbwarn messages but not the rest of the task's output, and
# a task that fails without an ERROR: line is only named in the cooker log.
work_dirs=()
for d in work work-shared; do
	if [ -d "$tmp_dir/$d" ]; then
		work_dirs+=("$tmp_dir/$d")
	fi
done
{
	for f in "$tmp_dir"/log/cooker/*/console-latest.log; do
		if [ -f "$f" ]; then
			sed -n 's/.*Logfile of failure stored in: //p' "$f"
		fi
	done
	if [ ${#work_dirs[@]} -gt 0 ]; then
		find "${work_dirs[@]}" -path '*/temp/log.do_*' -type l -print0 |
			{ xargs -0 -r grep -lsE '^(WARNING|ERROR):' || true; }
	fi
} | sort -u | while read -r log; do
	case "$log" in
	"$tmp_dir"/*) rel=${log#"$tmp_dir"/} ;;
	*) continue ;;
	esac
	mkdir -p "$dest/logs/$(dirname "$rel")"
	cp -L "$log" "$dest/logs/$rel"
done
