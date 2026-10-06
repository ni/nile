SUMMARY = "NI VirtualBench legacy Linux kernel"
DESCRIPTION = "Linux 3.2 kernel for the NI VirtualBench VB-8034"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=d7810fab7487fb0aad327b76f1be7cd7"

LINUX_VERSION = "3.2"
PV = "${LINUX_VERSION}+git"

SRC_URI = "git://github.com/ni/linux.git;protocol=https;branch=virtualbench/20.0/3.2 \
		   file://0001-linux-add-GCC-13-compiler-header.patch \
		   file://0002-scripts-dtc-avoid-duplicate-yylloc-definition.patch \
		   file://0003-ARM-8933-1-replace-Sun-Solaris-style-flag-on-section.patch \
		   file://0004-ARM-uaccess-keep-put_user-value-in-r2.patch \
		   file://0005-timeconst.pl-Eliminate-Perl-warning.patch \
		   file://0006-kernel-sched-build-at-O1-under-GCC-13.patch \
		   file://0007-ARM-8429-1-disable-GCC-SRA-optimization.patch"
SRCREV = "95415112dbd6becf2ca9676c28d945de6cb9c6df"

S = "${WORKDIR}/git"

ARCH = "arm"
KBUILD_DEFCONFIG = "ni_lci_defconfig"
KCONFIG_MODE = "alldefconfig"
KERNEL_IMAGETYPE = "uImage"
KERNEL_DEVICETREE = "ni-vb80x4.dtb"
KERNEL_DTBDEST = "boot"
KERNEL_FEATURES:remove = "cfg/fs/vfat.scc"
KERNEL_VERSION_SANITY_SKIP = "1"

# GCC 13 turns open-coded copy/clear loops into memcpy()/memset() calls where
# this 3.2 kernel cannot use them yet, hanging boot.
EXTRA_OEMAKE:append = ' KCFLAGS="-fno-tree-loop-distribute-patterns"'

COMPATIBLE_MACHINE = "^$"
COMPATIBLE_MACHINE:vb8034 = "vb8034"

require recipes-kernel/linux/linux-yocto.inc

do_kernel_configme:append:vb8034() {
	cp ${S}/arch/${ARCH}/configs/${KBUILD_DEFCONFIG} ${B}/.config
	# earlyprintk diagnostics; LL_UART defaults to UART0 = ttyPS0 on VB-8034.
	printf 'CONFIG_DEBUG_KERNEL=y\nCONFIG_DEBUG_LL=y\nCONFIG_EARLY_PRINTK=y\n' >> ${B}/.config
}
