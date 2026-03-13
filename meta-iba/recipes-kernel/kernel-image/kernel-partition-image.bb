SUMMARY = "Recipe to create an ext4 image containing the only /fitImage for RAUC update bundle"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit image

# Prevent standard rootfs packages from being installed
IMAGE_INSTALL = ""
IMAGE_LINGUAS = ""
PACKAGE_INSTALL = ""

IMAGE_FSTYPES = "ext4"
# These variables ensure the kernel image in the RAUC update bundles fit in the kernel partitions.
# Without these changes, Yocto adds overhead and extra space beyond the actual size of the rootfs
# image, which can cause the image to no longer fit in the partition.
IMAGE_OVERHEAD_FACTOR = "1.0"
IMAGE_ROOTFS_EXTRA_SPACE = "0"
# Setting IMAGE_ROOTFS_SIZE ensures the filesystem size on the kernel partitions will be stable
# and track the underlying partition size, even after installing an update bundle. Without this,
# the filesystem size will change each time an update bundle is installed and will be smaller
# than the actual partition size.
IMAGE_ROOTFS_SIZE = "${KERNEL_PARTITION_SIZE}"
# This variable ensures the build will fail if the image size exceeds the partition size for any reason.
IMAGE_ROOTFS_MAXSIZE = "${KERNEL_PARTITION_SIZE}"

do_rootfs[depends] += "virtual/kernel:do_deploy"

IMAGE_PREPROCESS_COMMAND += "fixup_image; "

fixup_image() {
    # Delete everything since we just want /fitImage
    rm -rf ${IMAGE_ROOTFS}/*

    cp ${DEPLOY_DIR_IMAGE}/fitImage ${IMAGE_ROOTFS}/fitImage
}
