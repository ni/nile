FILESEXTRAPATHS:prepend := "${THISDIR}/files/${COMPATIBLE_MACHINE}:"

SRC_URI:append:genesyszu = " \
	file://bsp.cfg \
	file://0001-digilent-net-Add-entry-point-for-setting-zynq_get-MA.patch \
	file://0002-digilent-net-Set-QSPI-read-settings-via-Kconfig.patch \
	file://0003-digilent-net-Set-MAC-address-from-QPSI-flash.patch \
	file://0004-digilent-net-Set-Kconfig-defaults-for-ZYNQ_GEM_MAC_Q.patch \
	file://0005-digilent-net-Read-MAC-address-from-OTP-section-of-QS.patch \
	file://0006-Implemented-workaround-for-Zynq-Ultrascale-SDR104-tu.patch \
"

COMPATIBLE_MACHINE:genesyszu-3eg = "genesyszu"
