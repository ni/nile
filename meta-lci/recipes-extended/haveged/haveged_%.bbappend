FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# meta-oe's haveged recipe installs no init script or service.
SRC_URI:append = " file://haveged.init file://haveged.service"

inherit update-rc.d systemd
INITSCRIPT_NAME = "haveged"
# After populate-volatile, which makes /run writable for its pidfile and socket.
INITSCRIPT_PARAMS = "start 38 S ."
SYSTEMD_SERVICE:${PN} = "haveged.service"

do_install:append() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/haveged.init ${D}${sysconfdir}/init.d/haveged
    install -d ${D}${systemd_system_unitdir}
    sed 's|@SBINDIR@|${sbindir}|' ${WORKDIR}/haveged.service > ${D}${systemd_system_unitdir}/haveged.service
}

FILES:${PN} += "${sysconfdir}/init.d/haveged ${systemd_system_unitdir}/haveged.service"
