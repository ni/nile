SUMMARY = "Userspace waiter for host-arm-dma interrupts"
DESCRIPTION = "Simple userspace app that blocks waiting on /dev/host-arm-dma events"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

SRC_URI = "file://host-arm-dma-wait.c \
		   file://host-arm-dma-uapi.h"

S = "${WORKDIR}"

do_compile() {
	${CC} ${CFLAGS} ${CPPFLAGS} ${LDFLAGS} host-arm-dma-wait.c -o host-arm-dma-wait
}

do_install() {
	install -d ${D}${bindir}
	install -m 0755 host-arm-dma-wait ${D}${bindir}/host-arm-dma-wait
}
