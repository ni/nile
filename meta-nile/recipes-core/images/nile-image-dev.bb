SUMMARY = "NI Linux Embedded Kula development image"

IMAGE_FEATURES += "splash ssh-server-openssh package-management debug-tweaks tools-sdk tools-debug"

IMAGE_INSTALL = "\
    packagegroup-core-boot \
    packagegroup-core-full-cmdline \
    ${CORE_IMAGE_EXTRA_INSTALL} \
    python3 \
    valgrind \
    "

inherit core-image
