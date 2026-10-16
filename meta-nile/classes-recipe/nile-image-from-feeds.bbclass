# With BUILD_IMAGES_FROM_FEEDS, rootfs_ipk stops do_rootfs from waiting for the
# image's packages to be written to DEPLOY_DIR_IPK. Wait for them here, so a
# feed in this build's DEPLOY_DIR_IPK has them when do_rootfs indexes it.
do_image_feed_packages[noexec] = "1"
do_image_feed_packages[recrdeptask] = "do_package_write_ipk"
addtask image_feed_packages before do_rootfs
