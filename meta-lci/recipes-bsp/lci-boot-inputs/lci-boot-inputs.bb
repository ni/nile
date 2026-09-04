SUMMARY = "VB-8034 boot build-time hardware inputs from the ni-lci feed"
DESCRIPTION = "FSBL ELF and FPGA bitstreams for the VB-8034 boot chain."
LICENSE = "CLOSED"

COMPATIBLE_MACHINE = "vb8034"
PACKAGE_ARCH = "${MACHINE_ARCH}"
INHIBIT_DEFAULT_DEPS = "1"
# ar (unpack the .ipk) and xz (its data.tar.xz payload).
DEPENDS = "binutils-native xz-native"

# Pinned to the ni-lci feed build that carries the boot-inputs IPK. Bump
# BOOT_INPUTS_VER and the checksum together with NILE_LCI_FEED_VER when the feed
# advances; the IPK version tracks the ni-central lci-legacy-artifacts build.
BOOT_INPUTS_VER ?= "27.0.0.5-0+d5"
BOOT_INPUTS_ARCH ?= "cortexa9t2hf-neon"
NILE_LCI_FEED_BASE ?= "http://argohttp.natinst.com/ni/linuxpkg/feeds/ipk/ni-l/ni-lci"
NILE_LCI_FEED_MAJOR ?= "27.0.0"

SRC_URI = "${NILE_LCI_FEED_BASE}/${NILE_LCI_FEED_MAJOR}/${BOOT_INPUTS_VER}/inline/lci-boot-inputs-vb8034_${BOOT_INPUTS_VER}_${BOOT_INPUTS_ARCH}.ipk;downloadfilename=lci-boot-inputs.ipk;unpack=0"
SRC_URI[sha256sum] = "3daa5df7bab9539aac13fb66b295ec06f58164306552e7205613cc5211d81bc0"

S = "${WORKDIR}"
LCI_BOOT_INPUTS_DIR = "${datadir}/lci-boot-inputs"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    # The IPK is an ar archive (debian-binary, control.tar.gz, data.tar.xz).
    cd ${WORKDIR}
    ar x lci-boot-inputs.ipk data.tar.xz
    tar -xJf data.tar.xz -C ${WORKDIR}
    install -d ${D}${LCI_BOOT_INPUTS_DIR}
    install -m 0644 ${WORKDIR}/usr/local/natinst/share/lci-boot-inputs/* ${D}${LCI_BOOT_INPUTS_DIR}/
}

SYSROOT_DIRS += "${LCI_BOOT_INPUTS_DIR}"

# Sysroot-only build inputs; nothing is packaged into an image.
PACKAGES = ""
EXCLUDE_FROM_WORLD = "1"
