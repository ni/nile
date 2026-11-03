
BRANCH = "master"
SRCREV = "e04ff21b1a160e2230ae96542464ac9467ecb62f"

# qemu-helper-native also stages generic qemu-native, which provides this header.
# Keep the Xilinx copy out of the shared native sysroot to avoid a file collision.
SYSROOT_DIRS_IGNORE += "${includedir}/qemu-plugin.h"
