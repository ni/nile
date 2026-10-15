SUMMARY = "VB-8034 SFP CD-ROM image for the update bundle"
DESCRIPTION = "sfp.iso from the lci-legacy-artifacts package in the ni-lci feed."
LICENSE = "CLOSED"

COMPATIBLE_MACHINE = "vb8034"
PACKAGE_ARCH = "${MACHINE_ARCH}"
INHIBIT_DEFAULT_DEPS = "1"
DEPENDS = "binutils-native xz-native"

LEGACY_ARTIFACTS_VER ?= "27.0.0.5-0+d5"
LEGACY_ARTIFACTS_ARCH ?= "cortexa9t2hf-neon"
NILE_LCI_FEED_BASE ?= "http://argohttp.natinst.com/ni/linuxpkg/feeds/ipk/ni-l/ni-lci"
NILE_LCI_FEED_MAJOR ?= "27.0.0"

SRC_URI = "${NILE_LCI_FEED_BASE}/${NILE_LCI_FEED_MAJOR}/${LEGACY_ARTIFACTS_VER}/inline/lci-legacy-artifacts-vb8034_${LEGACY_ARTIFACTS_VER}_${LEGACY_ARTIFACTS_ARCH}.ipk;downloadfilename=lci-legacy-artifacts.ipk;unpack=0"
SRC_URI[sha256sum] = "10b0a4418e73db16fe6001145e34ac0c8c075c45815f3a94ccf8d33e15ddc04d"

S = "${WORKDIR}"

inherit deploy

do_configure[noexec] = "1"
do_compile[noexec] = "1"
do_install[noexec] = "1"

do_deploy() {
    cd ${WORKDIR}
    ar x lci-legacy-artifacts.ipk data.tar.xz
    tar -xJf data.tar.xz -C ${WORKDIR}
    install -d ${DEPLOYDIR}
    install -m 0644 ${WORKDIR}/usr/local/natinst/share/lci/sfp.iso ${DEPLOYDIR}/sfp.iso
}
addtask deploy after do_compile

PACKAGES = ""
EXCLUDE_FROM_WORLD = "1"