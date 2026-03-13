IMAGE_INSTALL:append = " fit-image-stager"

# Only want boot.bin and boot.scr in the boot partition.
# Kernel/devicetree/etc will be in separate A/B kernel partitions and nothing else is needed.
IMAGE_BOOT_FILES = "boot.bin boot.scr"

# RAUC requires a block-based image to build update bundles for use with slots of
# type "raw" (which are required by the RAUC verity bundle format).
IMAGE_FSTYPES += "ext4"

# These variables ensure the rootfs image in the RAUC update bundles fit in the rootfs partitions.
# Without these changes, Yocto adds overhead and extra space beyond the actual size of the rootfs
# image which can cause the rootfs image to no longer fit in the partition.
IMAGE_OVERHEAD_FACTOR = "1.0"
IMAGE_ROOTFS_EXTRA_SPACE = "0"
# As a side-effect of overriding the above variables to keep the image from unnecessarily growing
# beyond the partition size, Yocto now has no extra space for some additional size overhead that
# is normally covered by those variables. Just set the rootfs size explicitly to the size of the
# partition.
IMAGE_ROOTFS_SIZE = "${ROOTFS_PARTITION_SIZE}"
# This variable ensures the build will fail if the image size exceeds the partition size for any reason.
IMAGE_ROOTFS_MAXSIZE = "${ROOTFS_PARTITION_SIZE}"
