SUMMARY = "NI Linux Embedded development image"

IMAGE_FEATURES += "splash ssh-server-openssh package-management debug-tweaks tools-sdk tools-debug"

IMAGE_INSTALL = "\
    packagegroup-core-boot \
    packagegroup-core-full-cmdline \
    ${CORE_IMAGE_EXTRA_INSTALL} \
    python3 \
    valgrind \
    devmem2 \
    tcpdump \
    "

IMAGE_INSTALL:append:kula = " host-arm-net-mod"

inherit core-image
