FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

KBUILD_DEFCONFIG:vb8034 = "nati_zynq_defconfig"
KCONFIG_MODE:vb8034 = "alldefconfig"
SRC_URI:append:vb8034 = " file://vb8034.cfg"
# Keep the defconfig's suffix rather than kernel-yocto's -yocto-standard.
LINUX_VERSION_EXTENSION:vb8034 = "-ni"

# The FIT carries the raw Image (KERNEL_IMAGETYPES in the machine conf); uImage
# satisfies packagegroup-core-boot's kernel-image-uimage rdepend.
KERNEL_IMAGETYPE:vb8034 = "uImage"
