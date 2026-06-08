SUMMARY = "Host ARM DMA example"
DESCRIPTION = "${SUMMARY}"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

inherit module

SRC_URI = "file://Makefile \
           file://host-arm-dma.c \
           file://host-arm-dma.conf \
          "

S = "${WORKDIR}"

# The inherit of module.bbclass will automatically name module packages with
# "kernel-module-" prefix as required by the oe-core build environment.

RPROVIDES:${PN} += "kernel-module-host-arm-dma"

do_install:append() {
    install -d ${D}${sysconfdir}/modprobe.d
    install -m 0644 ${WORKDIR}/host-arm-dma.conf ${D}${sysconfdir}/modprobe.d/host-arm-dma.conf
}

FILES:${PN} += "${sysconfdir}/modprobe.d/host-arm-dma.conf"
