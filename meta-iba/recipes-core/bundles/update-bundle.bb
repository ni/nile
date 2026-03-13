SUMMARY = "RAUC update bundle for ${MACHINE}"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit bundle

# Define compatibility and version
RAUC_BUNDLE_COMPATIBLE = "${MACHINE}"
RAUC_BUNDLE_VERSION = "v1.0"
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
# For local development builds you can generate a key and certificate file pair,
# uncomment these variables, and point them to your generated files. One way to
# generate a key and certificate pair with a 90 day expiration is shown here:
# openssl req -x509 -newkey rsa:4096 -nodes -keyout dev.key.pem -out dev.cert.pem -subj "/O=my-company/CN=my-cert-name" -days 90
#RAUC_KEY_FILE = "${THISDIR}/files/dev.key.pem"
#RAUC_CERT_FILE = "${THISDIR}/files/dev.cert.pem"
# You will also need to provide a public certificate that can be used to validate
# the update bundle signature. For local development builds, that public certificate
# can be the same as your RAUC_CERT_FILE above.
# See the comments in rauc-conf.bbappend for more information.
