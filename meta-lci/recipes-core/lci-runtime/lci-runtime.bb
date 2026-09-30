SUMMARY = "VB-8034 LCI runtime startup"
LICENSE = "CLOSED"

SRC_URI = "file://lci-runtime.init \
           file://lci-runtime.service \
           file://lci-runtime-prepare \
           file://90-lci-runtime.rules \
           "

inherit update-rc.d systemd
INITSCRIPT_NAME = "lci-runtime"
INITSCRIPT_PARAMS = "start 27 5 . stop 90 0 1 6 ."
SYSTEMD_SERVICE:${PN} = "lci-runtime.service"

# The feed packages don't declare vb8034Daemon's need for avahi-daemon and
# libatomic.
RDEPENDS:${PN} = "kernel-module-g-lci kernel-module-usb-f-hid-bulk libatomic avahi-daemon"

do_install() {
    install -d ${D}${libexecdir}/${BPN} ${D}${sysconfdir}/init.d ${D}${systemd_system_unitdir} \
        ${D}${sysconfdir}/udev/rules.d
    install -m 0755 ${WORKDIR}/lci-runtime-prepare ${D}${libexecdir}/${BPN}/
    sed 's|@LIBEXECDIR@|${libexecdir}/${BPN}|' ${WORKDIR}/lci-runtime.init > ${D}${sysconfdir}/init.d/lci-runtime
    chmod 0755 ${D}${sysconfdir}/init.d/lci-runtime
    sed 's|@LIBEXECDIR@|${libexecdir}/${BPN}|' ${WORKDIR}/lci-runtime.service \
        > ${D}${systemd_system_unitdir}/lci-runtime.service
    install -m 0644 ${WORKDIR}/90-lci-runtime.rules ${D}${sysconfdir}/udev/rules.d/
}

FILES:${PN} += "${sysconfdir}/init.d/lci-runtime ${systemd_system_unitdir}/lci-runtime.service \
    ${sysconfdir}/udev/rules.d/90-lci-runtime.rules"
