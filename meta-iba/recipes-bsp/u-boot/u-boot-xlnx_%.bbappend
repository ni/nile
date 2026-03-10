FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Apply the U-Boot configuration changes
SRC_URI += "file://env.cfg"
