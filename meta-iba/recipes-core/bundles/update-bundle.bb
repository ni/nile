SUMMARY = "RAUC update bundle for ${MACHINE}"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit nile-rauc-bundle

# Define compatibility and version
RAUC_BUNDLE_COMPATIBLE = "${MACHINE}"
RAUC_BUNDLE_FORMAT = "verity"

# Specify which slots to include (usually rootfs)
RAUC_BUNDLE_SLOTS = "rootfs kernel"

# Point to the image recipe that provides the rootfs
RAUC_SLOT_rootfs = "nile-image-dev"
RAUC_SLOT_rootfs[fstype] = "ext4"

# Point to the image recipe that provides the kernel
RAUC_SLOT_kernel = "kernel-partition-image"
RAUC_SLOT_kernel[fstype] = "ext4"

# Ensure the kernel partition image is complete before the bundle starts building
do_bundle[depends] += "kernel-partition-image:do_image_complete"

# Provide signing credentials (required)
# For local development builds we use the openssl-ca.sh script to generate
# local certificates that these locations point to. The key and certificate
# used here must match up with public certificates present in the image to
# validate updates; see comments in rauc-conf.bbappend for more information.
RAUC_KEY_FILE = "${THISDIR}/files/dev.key.pem"
RAUC_CERT_FILE = "${THISDIR}/files/dev.cert.pem"
