SUMMARY = "LCI target writable-volume mounts and U-Boot env configuration"
DESCRIPTION = "Boot steps that mount the persistent config and cal UBI volumes, \
set the hostname, and fix up unaligned accesses silently, for sysvinit and \
systemd, plus the fw_(printenv|setenv) configuration."
LICENSE = "CLOSED"

SRC_URI = "\
    file://lci-mountconfig.service \
    file://lci-mountcal.service \
    file://lci-populateconfig.service \
    file://lci-hostname.service \
    file://lci-alignment.conf \
    file://10-lci-eth0.network \
    file://sshdgenkeys-lci.conf \
    file://fw_env.config \
"

S = "${WORKDIR}"

inherit systemd
SYSTEMD_SERVICE:${PN} = "lci-mountconfig.service lci-mountcal.service lci-populateconfig.service lci-hostname.service"

# The scripts come from lciutils.
LCI_UTILS_DIR = "${libexecdir}/lciutils"

do_install() {
    if ${@bb.utils.contains('DISTRO_FEATURES', 'sysvinit', 'true', 'false', d)}; then
        # Mount the writable volumes early in sysinit; set the hostname after
        # initscripts' S39hostname.sh.
        install -d ${D}${sysconfdir}/rcS.d
        ln -sf ../..${LCI_UTILS_DIR}/mountconfig    ${D}${sysconfdir}/rcS.d/S36mountconfig
        ln -sf ../..${LCI_UTILS_DIR}/mountcal       ${D}${sysconfdir}/rcS.d/S36mountcal
        ln -sf ../..${LCI_UTILS_DIR}/populateconfig ${D}${sysconfdir}/rcS.d/S38populateconfig
        ln -sf ../..${LCI_UTILS_DIR}/lci-hostname   ${D}${sysconfdir}/rcS.d/S39lci-hostname
    fi

    install -d ${D}${systemd_system_unitdir} ${D}${nonarch_libdir}/tmpfiles.d
    for f in lci-mountconfig lci-mountcal lci-populateconfig lci-hostname; do
        sed 's|@LIBEXECDIR@|${LCI_UTILS_DIR}|' ${S}/$f.service \
            > ${D}${systemd_system_unitdir}/$f.service
    done
    install -m 0644 ${S}/lci-alignment.conf ${D}${nonarch_libdir}/tmpfiles.d/
    install -D -m 0644 ${S}/10-lci-eth0.network ${D}${systemd_unitdir}/network/10-lci-eth0.network
    # Generate the SSH host keys on the config volume once it is populated.
    install -D -m 0644 ${S}/sshdgenkeys-lci.conf \
        ${D}${systemd_system_unitdir}/sshdgenkeys.service.d/lci-config-volume.conf

    install -D -m 0644 ${S}/fw_env.config ${D}${sysconfdir}/fw_env.config
}

FILES:${PN} += "${sysconfdir}/fw_env.config ${systemd_system_unitdir} ${systemd_unitdir}/network \
    ${nonarch_libdir}/tmpfiles.d"

COMPATIBLE_MACHINE = "vb8034"
