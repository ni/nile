INITRAMFS_IMAGE = "nile-image-initramfs-boot"
# TODO: we should really have this bundled with the fit images and not the kernel?
INITRAMFS_IMAGE_BUNDLE = "1"

# when INITRAMFS_IMAGE is set, meta-xilinx appears to assume that you want to boot
# solely from an initramfs and makes an SD card image with just boot.bin, boot.scr,
# and the initramfs image; this conflicts with what we expect INITRAMFS_IMAGE to do
# when generating a FIT image, which is to add an initramfs image into the FIT. The
# following set of things undoes the changes that image-types-xilinx-qemu does in
# the "INITRAMFS_IMAGE is set" case to get us back to "please just turn our .wic
# into an sd card for qemu"
IMAGE_FSTYPES:remove = "cpio.gz.u-boot.qemu-sd-fatimg"
IMAGE_FSTYPES += "wic.qemu-sd"
QB_DEFAULT_FSTYPE = "wic.qemu-sd"
QB_ROOTFS = ""
QB_ROOTFS_OPT = "-drive if=sd,index=1,file=@ROOTFS@,format=raw"
