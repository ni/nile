
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI:append:class-nativesdk = " file://0001-qemu-xilinx-sched-attr-glibc-version.patch"

BRANCH = "master"
SRCREV = "e04ff21b1a160e2230ae96542464ac9467ecb62f"
