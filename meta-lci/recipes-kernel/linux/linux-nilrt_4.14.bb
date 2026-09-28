LINUX_VERSION = "4.14.146"

KBRANCH = "nilrt/master/4.14"
SRCREV = "94904f0d340e7c1913deae5064b7fc6cc3388344"

YOCTO_KMETA_BRANCH = "yocto-4.14"
SRCREV_meta = "c6adbc12d71acc04b9e2836cf738b3802f57f2ee"

require recipes-kernel/linux/linux-nilrt.inc

LIC_FILES_CHKSUM = "file://COPYING;md5=d7810fab7487fb0aad327b76f1be7cd7"

# The VirtualBench support in 4.14 is only for the VB-8034.
COMPATIBLE_MACHINE = "vb8034"

# VB doesn't build modules on-target; skip NILRT's target-runnable kbuild tools.
export NILRT_NO_ONTARGET_TOOLS = "1"
