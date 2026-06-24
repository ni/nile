FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://nile-selftest"

S = "${WORKDIR}"

do_install:append () {
	install -d ${D}${bindir}
	install -m 0755 ${S}/nile-selftest ${D}${bindir}
}
