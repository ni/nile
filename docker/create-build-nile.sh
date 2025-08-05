#!/bin/bash
set -u

SCRIPT_ROOT=$(realpath $(dirname ${BASH_SOURCE}))
PYREX_ROOT=$(realpath "${SCRIPT_ROOT}/../sources/pyrex")
PYREX_BASE=ubuntu-24.04-oe

IMAGE_NAME=build-nile

usage() {
	cat >&2 <<EOF
$(basename)
Creates a new build-nile container based on the garmin/pyrex containers.
EOF
	exit
}

positionals=()
while [ $# -ge 1 ]; do case "$1" in
	-h|--help)
		usage 0
		;;
	*)
		positionals+=($1)
		shift
		;;
esac
done

set -x

# parse out the short git hash, to tag this image with the current commit
pushd "${SCRIPT_ROOT}"
short_hash=$(git rev-parse --short HEAD)
popd

set -e
# build the pyrex base image
docker build \
	-f "${PYREX_ROOT}/image/Dockerfile" \
	-t "pyrex-base:${short_hash}" \
	--build-arg=PYREX_BASE=$PYREX_BASE \
	"${PYREX_ROOT}/image"

# build the build-nile image
docker build \
	-f "${SCRIPT_ROOT}/build-nile.Dockerfile" \
	-t "${IMAGE_NAME}:${short_hash}" \
	--build-arg=PYREX_IMAGE=pyrex-base:${short_hash} \
	"${SCRIPT_ROOT}"

# tag the image with the image version
docker tag \
	"${IMAGE_NAME}:${short_hash}" \
	"${IMAGE_NAME}:latest"

set +eux
