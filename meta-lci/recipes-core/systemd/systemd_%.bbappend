# The pinned systemd already has this meta-xilinx-core patch.
SRC_URI:remove = "file://0001-socket-downgrade-not-supported-logging-for-SO_PASSSE.patch"

# 32-bit time64 userspace on a pre-5.1 kernel needs room for glibc's converted
# timestamp cmsg.
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
SRC_URI:append = " file://0001-socket-util-reserve-cmsg-room-for-glibc-time64-conversion.patch"
