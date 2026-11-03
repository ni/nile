FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Install /etc/fw_env.config to tell fw_printenv/fw_setenv where the u-boot environment is stored
SRC_URI += "file://fw_env.config"

do_install:append() {
    install -d ${D}${sysconfdir}
    install -m 0644 ${UNPACKDIR}/fw_env.config ${D}${sysconfdir}/fw_env.config
}
