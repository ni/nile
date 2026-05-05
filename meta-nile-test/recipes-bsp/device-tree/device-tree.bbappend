FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SYSTEM_USER_DTSI ?= "${MACHINE_ARCH}/system-user.dtsi"

COMPATIBLE_MACHINE:genesyszu-3eg = ".*"
SRC_URI:append:genesyszu-3eg = " \
		file://${SYSTEM_USER_DTSI} \
		"

do_configure:append() {
	cp ${WORKDIR}/${SYSTEM_USER_DTSI} ${B}/device-tree
	echo "#include \"system-user.dtsi\"" >> ${B}/device-tree/system-top.dts
}
