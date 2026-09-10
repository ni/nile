SUMMARY = "LCI target writable-volume mounts and U-Boot env configuration"
DESCRIPTION = "Boot steps that mount the persistent config and cal UBI volumes, \
for sysvinit and systemd, plus the fw_(printenv|setenv) configuration."
LICENSE = "CLOSED"

SRC_URI = "\
    file://lci-mountconfig.service \
    file://lci-mountcal.service \
    file://lci-populateconfig.service \
    file://fw_env.config \
"

S = "${WORKDIR}"

inherit systemd
SYSTEMD_SERVICE:${PN} = "lci-mountconfig.service lci-mountcal.service lci-populateconfig.service"

# The scripts come from lciutils.
LCI_UTILS_DIR = "${libexecdir}/lciutils"

do_install() {
    if ${@bb.utils.contains('DISTRO_FEATURES', 'sysvinit', 'true', 'false', d)}; then
        # Mount the writable volumes early in sysinit.
        install -d ${D}${sysconfdir}/rcS.d
        ln -sf ../..${LCI_UTILS_DIR}/mountconfig    ${D}${sysconfdir}/rcS.d/S36mountconfig
        ln -sf ../..${LCI_UTILS_DIR}/mountcal       ${D}${sysconfdir}/rcS.d/S36mountcal
        ln -sf ../..${LCI_UTILS_DIR}/populateconfig ${D}${sysconfdir}/rcS.d/S38populateconfig
    fi

    install -d ${D}${systemd_system_unitdir}
    for f in lci-mountconfig lci-mountcal lci-populateconfig; do
        sed 's|@LIBEXECDIR@|${LCI_UTILS_DIR}|' ${S}/$f.service \
            > ${D}${systemd_system_unitdir}/$f.service
    done
    install -D -m 0644 ${S}/fw_env.config ${D}${sysconfdir}/fw_env.config
}

FILES:${PN} += "${sysconfdir}/fw_env.config ${systemd_system_unitdir}"

COMPATIBLE_MACHINE = "vb8034"
