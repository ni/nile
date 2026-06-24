# nile-selftest is a generic entrypoint
# Every device in NILE should expose a machine-specific "nile-selftest"
# script that performs some sort of simple "hardware is working"
# verification, and install it to ${bindir}/nile-selftest.
#
# meta-nile then contains the infrastructure to run that as a ptest.
#
# Devices are permitted to add other ptests, and are encouraged to do so.
# However, abstracting out a self-test in this manner lets us do two things:
#
# - /usr/bin/nile-selftest becomes a known entrypoint that can function
#   without ptests, should we choose to use a different test executor in the
#   future
# - having this have a known, consistent name across all devices lets the
#   NILE team more accurately assess platform health without needing to know
#   details about the inner workings of individual products.

SUMMARY = "NILE Self Test"
DESCRIPTION = "Target-specific self-test"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"
SECTION = "base"

inherit ptest

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://run-ptest"

S = "${WORKDIR}"

# nile-selftest will be an empty package if no client layer has a .bbappend
# to install a nile-selftest executable. (The ptest runner handles this
# condition as a failure.)
ALLOW_EMPTY:${PN} = "1"

# This recipe handles the qemu* targets directly, as those are expected to
# be handled completely with meta-nile (e.g. no target-specific client layer).
# Client layers should have their own .bbappend to install their own 'nile-selftest'.
SRC_URI:append:qemuall = " file://nile-selftest-qemu"

do_install:append:qemuall () {
        install -d ${D}${bindir}
        install -m 0755 ${S}/nile-selftest-qemu ${D}${bindir}/nile-selftest
}
