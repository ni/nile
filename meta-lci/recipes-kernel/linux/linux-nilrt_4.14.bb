SUMMARY = "NI Linux RT 4.14 kernel for VirtualBench"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=d7810fab7487fb0aad327b76f1be7cd7"

LINUX_VERSION = "4.14"
PV = "${LINUX_VERSION}+git"

SRC_URI = "git://github.com/ni/linux.git;protocol=https;branch=nilrt/master/4.14"
SRCREV = "94904f0d340e7c1913deae5064b7fc6cc3388344"

S = "${WORKDIR}/git"

ARCH = "arm"
KBUILD_DEFCONFIG = "nati_zynq_defconfig"
KCONFIG_MODE = "alldefconfig"
# The FIT carries the raw Image (KERNEL_IMAGETYPES in the machine conf); uImage
# satisfies packagegroup-core-boot's kernel-image-uimage rdepend.
KERNEL_IMAGETYPE = "uImage"
KERNEL_VERSION_SANITY_SKIP = "1"

# VB doesn't build modules on-target; skip NILRT's target-runnable kbuild tools.
export NILRT_NO_ONTARGET_TOOLS = "1"

# NILRT ships a plain defconfig, not yocto-kernel-cache .scc metadata.
KERNEL_DANGLING_FEATURES_WARN_ONLY = "1"
KERNEL_FEATURES:remove = "cfg/fs/vfat.scc"

COMPATIBLE_MACHINE = "^$"
COMPATIBLE_MACHINE:vb8034 = "vb8034"

require recipes-kernel/linux/linux-yocto.inc

do_kernel_configme:append:vb8034() {
        cp ${S}/arch/${ARCH}/configs/${KBUILD_DEFCONFIG} ${B}/.config
        # The root is a squashfs mounted directly (no initramfs).
        printf 'CONFIG_SQUASHFS=y\nCONFIG_SQUASHFS_LZO=y\n' >> ${B}/.config
        # g_lci and its ChipIdea UDC are built in so the g_lci.* boot options
        # apply. The UDC needs the usb-nop-xceiv PHY built in.
        printf 'CONFIG_USB_GADGET=y\nCONFIG_USB_CHIPIDEA=y\nCONFIG_USB_CHIPIDEA_UDC=y\nCONFIG_USB_ULPI_BUS=y\nCONFIG_USB_CHIPIDEA_ULPI=y\nCONFIG_NOP_USB_XCEIV=y\nCONFIG_USB_LIBCOMPOSITE=y\nCONFIG_USB_F_HID_BULK=y\nCONFIG_USB_F_MASS_STORAGE=y\nCONFIG_USB_G_LCI=y\n' >> ${B}/.config
        # The LCI daemon drives the FPGA through /dev/uio0 (uio_lci).
        printf 'CONFIG_UIO=y\nCONFIG_UIO_LCI=y\n' >> ${B}/.config
        # Zynq SWDT for the daemon, nowayout.
        printf 'CONFIG_WATCHDOG=y\nCONFIG_WATCHDOG_CORE=y\nCONFIG_WATCHDOG_NOWAYOUT=y\nCONFIG_CADENCE_WATCHDOG=y\n' >> ${B}/.config
        # LCI clients see the IPv6 addresses in the device info.
        printf 'CONFIG_IPV6=y\n' >> ${B}/.config
        # systemd-networkd needs network namespaces.
        if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
            printf 'CONFIG_NAMESPACES=y\nCONFIG_UTS_NS=y\nCONFIG_IPC_NS=y\nCONFIG_PID_NS=y\nCONFIG_NET_NS=y\n' >> ${B}/.config
        fi
}
