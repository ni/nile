# Extension of the RAUC "bundle" class for NILE images.

inherit bundle

# Normal NILE partition layout for RAUC expects a rootfs and a kernel slot
RAUC_BUNDLE_SLOTS ?= "rootfs kernel"

python do_configure:prepend() {
    # We would like the RAUC bundle version and build to match the content
    # of the os-release file inside of the image. Unfortunately, at bundle
    # build time, what we have is a rootfs filesystem image, from which
    # we'd have to reextract /usr/lib/os-release.
    #
    # Instead of doing that, take an easier approach; the image build
    # process dumps out all of the environment variables that went into the
    # image recipe build into a testdata.json file, so read variables out of
    # that in order to auto-set the RAUC_BUNDLE_* variables to match
    # the same things that nile-image-info.bbclass puts into os-release.

    import json
    import os

    testdata_path = d.expand("${DEPLOY_DIR_IMAGE}/${RAUC_SLOT_rootfs}${IMAGE_MACHINE_SUFFIX}${IMAGE_NAME_SUFFIX}.testdata.json")
    if os.path.isfile(testdata_path):
        bb.note("Using testdata at %s to populate RAUC bundle variables" % testdata_path)
        with open(testdata_path) as j:
            rootfs_data = json.load(j)

        if "IMAGE_BUILD_ID" in rootfs_data:
            bb.note("setting RAUC_BUNDLE_BUILD to %s from rootfs testdata" % rootfs_data["IMAGE_BUILD_ID"])
            d.setVar("RAUC_BUNDLE_BUILD", rootfs_data["IMAGE_BUILD_ID"])
        if "BUILDNAME" in rootfs_data:
            bb.note("setting RAUC_BUNDLE_VERSION to %s from rootfs testdata" % rootfs_data["BUILDNAME"])
            d.setVar("RAUC_BUNDLE_VERSION", rootfs_data["BUILDNAME"])
}
