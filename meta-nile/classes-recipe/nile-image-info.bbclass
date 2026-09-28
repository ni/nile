# Adds image-related content to os-release.
#
# This has to happen here rather than during the os-release.bb recipe,
# because at os-release.bb build time we don't know what image we're
# building that into.
#
# See https://www.freedesktop.org/software/systemd/man/latest/os-release.html
# for more information about os-release fields.

def image_uuid(d):
    import hashlib
    import uuid

    # Stable definition of a UUID namespace for generating v5 UUIDs
    namespace_uuid = uuid.UUID('cdd36c51-e9b9-49ad-9604-4acbe8320107')
    hash = d.getVar("BB_TASKHASH")
    return str(uuid.uuid5(namespace_uuid, hash))

python add_nile_image_info () {
    # os-release.bb installs the file to this location
    osrelease_path = d.getVar("IMAGE_ROOTFS") + "/" + d.getVar("nonarch_libdir") + "/os-release"

    d.setVar("IMAGE_BUILD_ID", image_uuid(d))

    with open(osrelease_path, "a") as writer:
        # "A string uniquely identifying the system image originally used as the
        # installation base." We use an opaque UUID derived from the OE task hash.
        writer.write('BUILD_ID={0}\n'.format(d.getVar("IMAGE_BUILD_ID")))

        # "A lower-case string identifying a specific image of the operating
        # system." We use the recipe name of this image.
        writer.write('IMAGE_ID={0}\n'.format(d.getVar("PN")))

        # "A string identifying the OS image version". We use BUILDNAME.
        writer.write('IMAGE_VERSION="{0}"\n'.format(d.getVar("BUILDNAME")))
}

# We prepend to ensure this happens early; we want to make sure that
# IMAGE_BUILD_ID is set at testdata.json generation time.
ROOTFS_POSTPROCESS_COMMAND:prepend = " add_nile_image_info "
