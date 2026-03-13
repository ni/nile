FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# To install RAUC update bundles on a system, the system must have a keyring
# file containing a certificate that can be used to validate the bundle signature.
# Uncomment these lines and point them to your file.
#RAUC_KEYRING_FILE = "ca.cert.pem"
#SRC_URI:append = " file://${RAUC_KEYRING_FILE}"
# Whatever filename you choose for RAUC_KEYRING_FILE, you must also have a matching
# keyring path entry in system.conf to match. Here is an example:
#
# [keyring]
# path=ca.cert.pem
