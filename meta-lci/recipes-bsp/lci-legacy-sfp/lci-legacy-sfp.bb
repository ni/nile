SUMMARY = "VB-8034 legacy SFP CD-ROM image for the update bundle"
DESCRIPTION = "Build-time input extracted from the pinned ni-lci legacy-artifacts feed package."
LICENSE = "CLOSED"

COMPATIBLE_MACHINE = "vb8034"
PACKAGE_ARCH = "${MACHINE_ARCH}"
INHIBIT_DEFAULT_DEPS = "1"
DEPENDS = "binutils-native xz-native"

LEGACY_ARTIFACTS_VER ?= "27.0.0.1-0+d1"
LEGACY_ARTIFACTS_ARCH ?= "cortexa9t2hf-neon"
NILE_LCI_FEED_BASE ?= "http://argohttp.natinst.com/ni/linuxpkg/feeds/ipk/ni-l/ni-lci"
NILE_LCI_FEED_MAJOR ?= "27.0.0"

SRC_URI = "${NILE_LCI_FEED_BASE}/${NILE_LCI_FEED_MAJOR}/${LEGACY_ARTIFACTS_VER}/inline/lci-legacy-artifacts-vb8034_${LEGACY_ARTIFACTS_VER}_${LEGACY_ARTIFACTS_ARCH}.ipk;downloadfilename=lci-legacy-artifacts.ipk;unpack=0"
SRC_URI[sha256sum] = "1fcfbecc513b918d27af2bdcc29e2ffbb26df3fc11cd8a49c772492b98fc79f6"

S = "${WORKDIR}"
LCI_LEGACY_SFP_DIR = "${datadir}/lci-legacy-sfp"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    cd ${WORKDIR}
    ar x lci-legacy-artifacts.ipk data.tar.xz
    tar -xJf data.tar.xz -C ${WORKDIR}
    install -d ${D}${LCI_LEGACY_SFP_DIR}
    install -m 0644 ${WORKDIR}/usr/local/natinst/share/lci/sfp.iso ${D}${LCI_LEGACY_SFP_DIR}/sfp.iso
}

SYSROOT_DIRS += "${LCI_LEGACY_SFP_DIR}"
PACKAGES = ""
EXCLUDE_FROM_WORLD = "1"