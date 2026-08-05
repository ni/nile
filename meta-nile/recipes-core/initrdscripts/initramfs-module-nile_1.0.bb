SUMMARY = "initramfs modules for NILE"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://nile-overlay.sh"

S = "${WORKDIR}"

do_install() {
	install -d ${D}/init.d

	install -m 0755 ${WORKDIR}/nile-overlay.sh ${D}/init.d/91-nileoverlay
}

PACKAGES = "initramfs-module-nile-overlay"

SUMMARY:initramfs-module-nile-overlay = "initramfs support for mounting NILE data partition as overlay"
RDEPENDS:initramfs-module-nile-overlay = "initramfs-framework-base"
RCONFLICTS:initramfs-module-nile-overlay = "initramfs-module-overlayroot"
FILES:initramfs-module-nile-overlay = "/init.d/91-nileoverlay"

inherit allarch
