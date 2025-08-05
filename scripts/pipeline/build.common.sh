set -e

SCRIPT_ROOT=$(realpath $(dirname $BASH_SOURCE))

. "${SCRIPT_ROOT}/../../nile-oe-init-build-env" $@

pyrex-run bitbake --version
