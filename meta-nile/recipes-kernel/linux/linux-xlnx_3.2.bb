SUMMARY = "NI VirtualBench legacy Linux kernel"
DESCRIPTION = "Linux 3.2 kernel for the NI VirtualBench VB-8034"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=d7810fab7487fb0aad327b76f1be7cd7"

LINUX_VERSION = "3.2"
PV = "${LINUX_VERSION}+git"

SRC_URI = "git://github.com/ni/linux.git;protocol=https;branch=virtualbench/20.0/3.2 \
		   file://0001-linux-add-gcc13-compiler-header.patch \
		   file://0002-scripts-dtc-avoid-duplicate-yylloc-definition.patch \
		   file://0003-arm-proc-v7-use-modern-section-flags.patch \
		   file://0004-arm-uaccess-keep-put-user-value-in-r2.patch \
		   file://0005-kernel-timeconst-use-array-definedness.patch \
		   file://0006-arm-compressed-head-use-modern-section-flags.patch \
		   file://0007-arm-compressed-piggy-use-modern-section-flags.patch \
		   file://0008-kernel-sched-build-at-O1-for-gcc13.patch"
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

# GCC 13 miscompiles this legacy 3.2 code without these: the loop-idiom pass emits
# memcpy/memset before they are usable, NULL-check deletion drops valid early
# accesses, and IPA-SRA's build_sched_domains.isra clone passes a bogus sched_domain
# child pointer -> all silent hangs/oopses at boot. Env var so make imports it into
# KBUILD_CFLAGS (Makefile: KBUILD_CFLAGS += $(KCFLAGS)).
export KCFLAGS = "-fno-tree-loop-distribute-patterns -fno-delete-null-pointer-checks -fno-ipa-sra"

COMPATIBLE_MACHINE = "^$"
COMPATIBLE_MACHINE:vb8034 = "vb8034"

require recipes-kernel/linux/linux-yocto.inc

do_kernel_configme:append:vb8034() {
	cp ${S}/arch/${ARCH}/configs/${KBUILD_DEFCONFIG} ${B}/.config
	# earlyprintk diagnostics; LL_UART defaults to UART0 = ttyPS0 on VB-8034.
	printf 'CONFIG_DEBUG_KERNEL=y\nCONFIG_DEBUG_LL=y\nCONFIG_EARLY_PRINTK=y\n' >> ${B}/.config
}
