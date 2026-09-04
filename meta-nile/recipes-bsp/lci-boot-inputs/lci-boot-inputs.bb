SUMMARY = "VB-8034 boot build-time hardware inputs from the ni-lci feed"
DESCRIPTION = "FSBL ELF and FPGA bitstreams for the VB-8034 boot chain, staged \
from the ni-lci feed's lci-boot-inputs-vb8034 IPK (built by ni-central \
lci-legacy-artifacts). Build-time only: consumed by lci-fitimage, fsbl, and \
bitstream to assemble the FIT and boot.bin; never installed on the device."
LICENSE = "CLOSED"

COMPATIBLE_MACHINE = "vb8034"
PACKAGE_ARCH = "${MACHINE_ARCH}"
INHIBIT_DEFAULT_DEPS = "1"
# ar (unpack the .ipk) and xz (its data.tar.xz payload).
DEPENDS = "binutils-native xz-native"

# Pinned to the ni-lci feed build that carries the boot-inputs IPK. Bump
# BOOT_INPUTS_VER and the checksum together with NILE_LCI_FEED_VER when the feed
# advances; the IPK version tracks the ni-central lci-legacy-artifacts build.
BOOT_INPUTS_VER ?= "26.8.0.7-0+d7"
BOOT_INPUTS_ARCH ?= "cortexa9hf-vfpv3"
NILE_LCI_FEED_BASE ?= "http://argohttp.natinst.com/ni/linuxpkg/feeds/ipk/ni-l/ni-lci"
NILE_LCI_FEED_MAJOR ?= "26.8.0"

SRC_URI = "${NILE_LCI_FEED_BASE}/${NILE_LCI_FEED_MAJOR}/${BOOT_INPUTS_VER}/inline/lci-boot-inputs-vb8034_${BOOT_INPUTS_VER}_${BOOT_INPUTS_ARCH}.ipk;downloadfilename=lci-boot-inputs.ipk;unpack=0"
SRC_URI[sha256sum] = "ca7c733951f1e4dee471ad6e408363cf23e6a4b4d88644b2fb91ba54d782d61f"

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
