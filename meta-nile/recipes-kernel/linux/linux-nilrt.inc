SUMMARY = "NI Linux kernel (linux-nilrt)"
DESCRIPTION = "Linux kernel"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

inherit kernel

FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

PV = "${LINUX_VERSION}"

KBRANCH ?= "undefined"
SRCBRANCHARG = "${@['nobranch=1', 'branch=${KBRANCH}'][d.getVar('KBRANCH', True) != '']}"

KERNELURI ?= "git://github.com/ni/linux.git;protocol=https;name=machine"
YOCTO_META ?= "git://git.yoctoproject.org/yocto-kernel-cache;type=kmeta;name=meta;branch=${YOCTO_KMETA_BRANCH};destsuffix=yocto-kmeta"
SRC_URI = "${KERNELURI};${SRCBRANCHARG} ${YOCTO_META}"

require recipes-kernel/linux/linux-yocto.inc
