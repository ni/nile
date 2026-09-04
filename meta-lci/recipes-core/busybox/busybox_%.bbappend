FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

# S39hostname.sh runs /bin/hostname.
SRC_URI:append:vb8034 = " file://hostname.cfg"
