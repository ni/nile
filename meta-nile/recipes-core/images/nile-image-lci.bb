SUMMARY = "LCI legacy-compatible production image (update-capable)"
DESCRIPTION = "Production image for shipped LCI devices (e.g. VB-8034). Delivers a \
read-only squashfs root inside the legacy lci.itb / UBI update contract. \
Unlike nile-image-dev it carries no developer tweaks. See \
nile-image-builder-port-plan.md."
LICENSE = "MIT"

COMPATIBLE_MACHINE = "vb8034"

IMAGE_FEATURES += "ssh-server-openssh"

IMAGE_INSTALL = "\
    packagegroup-core-boot \
    ${CORE_IMAGE_EXTRA_INSTALL} \
    "

# LCI runtime, installed from the ni-lci feed. These are feed-only packages
# (no local OE recipe), so use IMAGE_INSTALL_NODEPS; opkg resolves their runtime
# Depends from the configured feeds at do_rootfs.
IMAGE_INSTALL_NODEPS:append = " nilcidriver-vb8034 lciutils lci-legacy-artifacts-vb8034"

# Read-only squashfs is the updater's root payload; the FIT lci.itb and bootfs
# UBI volume are produced by lci-fitimage (EXTRA_IMAGEDEPENDS in vb8034.conf).
IMAGE_FSTYPES = "squashfs"

inherit core-image

# From-feeds images carry no build-time dep on the feed packages, so native tools
# their preinsts / image commands invoke are not staged transitively:
#  - useradd   (shadow-native): dbus-common preinst creating "messagebus"
#  - systemctl (systemd-systemctl-native): systemd_preset_all at do_image
do_rootfs[depends] += "shadow-native:do_populate_sysroot systemd-systemctl-native:do_populate_sysroot"

# --- LCI update bundle (see the port plan's contract surface) ----------------
# Device identity (MANIFEST_*) and the bundle file name (LCI_BUNDLE_NAME) are
# device-specific and set by the machine conf (e.g. conf/machine/vb8034.conf).
# DeviceCode is gated against the U-Boot env by the device-side
# firmware_update.sh, so it must match the device exactly.
LCI_FW_VERSION ?= "${DISTRO_VERSION}"

# Firmware signing.
#  - LCI_SIGN_METHOD=linuxsigning (the pipeline path): production 'lci' key on
#    the NI signing server (ssh, same interface as ni-central nifwsigning). All
#    farm pipeline builds -- PR and official alike -- sign this way so a signed
#    bundle can be device-tested before merge. Only authorized build machines
#    may use the key; it is not available to local developer builds.
#  - LCI_SIGN_METHOD=local: optional offline convenience -- sign with a
#    developer-supplied key (openssl) for a test device provisioned with the
#    matching public key. Not a separate key "family"; just a local fallback.
#  - LCI_SIGN_METHOD=none (default): unsigned; not device-acceptable.
# The rootfs public key at /etc/natinst/lci.pem must match whichever key signs.
LCI_SIGN_METHOD ?= "none"
# Whether a signing failure is fatal. Official/CI builds set 1 (never emit an
# unsigned official bundle); PR builds leave 0 (signing outage doesn't red-wall
# validation -- the bundle is just left unsigned).
LCI_SIGN_REQUIRED ?= "0"
LCI_SIGN_TOOL ?= "ssh -oBatchMode=yes -oConnectTimeout=30 mrsign@linux.signing.ni.systems --"
LCI_SIGN_KEY ?= "lci"
LCI_SIGN_DIGEST ?= "sha256"
# Path to a developer/test private key PEM (LCI_SIGN_METHOD=local).
LCI_SIGN_LOCAL_KEY ?= ""

do_bundle[depends] += "lci-fitimage:do_deploy openssl-native:do_populate_sysroot"
# linuxsigning reaches the signing server over the network; ssh is a host tool.
do_bundle[network] = "1"
HOSTTOOLS += "ssh"
do_bundle() {
    local work="${WORKDIR}/cfg"
    rm -rf "${work}"
    install -d "${work}"

    if [ -z "${LCI_BUNDLE_NAME}" ] || [ -z "${MANIFEST_DEVICECODE}" ]; then
        bbfatal "LCI_BUNDLE_NAME and MANIFEST_DEVICECODE must be set by the machine conf (see conf/machine/vb8034.conf)."
    fi

    cat > "${work}/firmware.info" <<EOF
# Firmware meta-data
TargetClass=${MANIFEST_TARGETCLASS}
DeviceCode=${MANIFEST_DEVICECODE}
DeviceDesc=${MANIFEST_DEVICEDESC}
Version=${LCI_FW_VERSION}
EOF

    install -m 0644 "${DEPLOY_DIR_IMAGE}/lci-kernel.itb" "${work}/lci.itb"
    # Read the persistent squashfs symlink from DEPLOY_DIR_IMAGE (IMGDEPLOYDIR is
    # emptied once do_image_complete is restored from sstate).
    install -m 0644 "${DEPLOY_DIR_IMAGE}/${IMAGE_LINK_NAME}.squashfs" "${work}/root.squashfs"
    # SFP stand-in (2048-aligned zero image). The approved payload is delivered
    # by the lci-legacy-artifacts IPK once published.
    dd if=/dev/zero of="${work}/sfp.iso" bs=2048 count=1 2>/dev/null

    # The updater ubiupdatevol's these with -s <size>; they must be 2048-aligned.
    # (awk, not expr: expr exits 1 when the remainder is 0, tripping set -e.)
    for f in root.squashfs sfp.iso; do
        sz=`stat -c%s "${work}/${f}"`
        rem=`awk "BEGIN{print ${sz}%2048}"`
        if [ "${rem}" != "0" ]; then
            bbfatal "${f} size (${sz}) is not a multiple of 2048 bytes"
        fi
    done

    case "${LCI_SIGN_METHOD}" in
      linuxsigning)
        # linuxsigning detached-signature interface (see ni-central
        # src/daqmx/firmware/nifwsigning). Production 'lci' key. Non-fatal on
        # failure unless LCI_SIGN_REQUIRED=1 (official builds).
        sign_failed=0
        for f in lci.itb root.squashfs sfp.iso; do
            if ! ${LCI_SIGN_TOOL} sign --key ${LCI_SIGN_KEY} --digest ${LCI_SIGN_DIGEST} \
                   --comment "\"nile ${MANIFEST_DEVICEDESC} ${LCI_FW_VERSION}\"" \
                   < "${work}/${f}" > "${work}/${f}.sig"; then
                sign_failed=1
                break
            fi
        done
        if [ "${sign_failed}" = "1" ]; then
            rm -f "${work}"/*.sig
            if [ "${LCI_SIGN_REQUIRED}" = "1" ]; then
                bbfatal "linuxsigning failed (LCI_SIGN_REQUIRED=1): refusing to emit an unsigned official ${LCI_BUNDLE_NAME}."
            else
                bbwarn "linuxsigning failed; emitting UNSIGNED ${LCI_BUNDLE_NAME} (non-fatal, LCI_SIGN_REQUIRED=0)."
            fi
        fi
        ;;
      local)
        if [ ! -f "${LCI_SIGN_LOCAL_KEY}" ]; then
            bbfatal "LCI_SIGN_METHOD=local but LCI_SIGN_LOCAL_KEY (${LCI_SIGN_LOCAL_KEY}) is not a file"
        fi
        # Same detached format the device verifies with the matching dev pubkey.
        for f in lci.itb root.squashfs sfp.iso; do
            openssl dgst -sha256 -sign "${LCI_SIGN_LOCAL_KEY}" -binary \
                -out "${work}/${f}.sig" "${work}/${f}"
        done
        ;;
      *)
        bbwarn "LCI_SIGN_METHOD=none: ${LCI_BUNDLE_NAME} is UNSIGNED. Set 'local' (dev key) or 'linuxsigning' (production 'lci' key) for a device-acceptable bundle."
        ;;
    esac

    tar -czf "${DEPLOY_DIR_IMAGE}/${LCI_BUNDLE_NAME}" --owner=root --group=root -C "${work}" .
    install -m 0644 "${work}/firmware.info" "${DEPLOY_DIR_IMAGE}/firmware.info"
}
addtask bundle after do_image_complete before do_build
