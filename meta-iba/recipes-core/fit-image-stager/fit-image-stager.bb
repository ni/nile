SUMMARY = "Stages fitImage into a directory under /boot for later reference in wks file"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# Ensure the kernel has finished and deployed before this recipe runs
do_install[depends] += "virtual/kernel:do_deploy"

FIT_DEST_PATH = "/boot/fit-images"

do_install() {
    install -d ${D}${FIT_DEST_PATH}
    if [ -f "${DEPLOY_DIR_IMAGE}/fitImage" ]; then
        install -m 0644 ${DEPLOY_DIR_IMAGE}/fitImage ${D}${FIT_DEST_PATH}/fitImage
    else
        bbfatal "Required file not found: ${DEPLOY_DIR_IMAGE}/fitImage"
    fi
}

FILES:${PN} += "${FIT_DEST_PATH}/fitImage"

