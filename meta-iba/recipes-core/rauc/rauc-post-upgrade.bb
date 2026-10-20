SUMMARY = "RAUC post-upgrade service"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/meta/COPYING.MIT;md5=3da9cfbcb788c80a0384361b4de20420"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

inherit systemd

SYSTEMD_PACKAGES += "${PN}"
SYSTEMD_SERVICE:${PN} = "rauc-post-upgrade.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

SRC_URI += "\
	file://rauc-post-upgrade.service \
	file://rauc-post-upgrade \
"

S = "${WORKDIR}"

do_install:append () {
	install -d ${D}${systemd_unitdir}/system/
	install -m 0644 ${S}/rauc-post-upgrade.service ${D}${systemd_unitdir}/system/
	install -d ${D}${bindir}
	install -m 0755 ${S}/rauc-post-upgrade ${D}${bindir}/
}

FILES:${PN} = "\
	${systemd_unitdir}/system/rauc-post-upgrade.service \
	${bindir}/rauc-post-upgrade \
"
