FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

UBOOTURI:vb8034 = "git://github.com/ni/u-boot.git;protocol=https"
UBRANCH:vb8034 = "lci/20.0.0"
SRCREV:vb8034 = "16f92d12ef2f814e793bef4916f6a9db3d369b05"
LIC_FILES_CHKSUM:vb8034 = "file://README;beginline=1;endline=4;md5=6f91eb9f8982008da7a93c6941d78813"
UBOOT_INITIAL_ENV:vb8034 = ""
UBOOT_ELF:vb8034 = "u-boot"

SRC_URI:append:vb8034 = " \
	file://0001-u-boot-add-gcc13-compiler-header.patch \
	file://0002-u-boot-host-fdt-include-order.patch \
	file://0003-u-boot-gcc13-fix-legacy-led-aliases.patch \
	file://0004-u-boot-gcc13-restore-gnu89-inline.patch \
"

# This legacy U-Boot tree uses boards.cfg and does not consume a generated
# device tree. Avoid attaching the unavailable XSCT DTB provider to its
# configure task or passing an EXT_DTB make argument.
PREFERRED_PROVIDER_virtual/dtb:pn-u-boot-xlnx:vb8034 = ""
DTB_FILE_NAME:pn-u-boot-xlnx:vb8034 = ""

do_configure:prepend:vb8034 () {
	unset LDFLAGS
}

do_configure:vb8034 () {
	unset LDFLAGS
	oe_runmake -C ${S} O=${B} ${UBOOT_MACHINE}
}
