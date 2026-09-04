SUMMARY = "Production firmware image for LCI"
LICENSE = "MIT"

COMPATIBLE_MACHINE = "vb8034"
DEPENDS += "lci-legacy-sfp"

IMAGE_FEATURES += "ssh-server-openssh"

# Root is a read-only squashfs: run postinsts and update-alternatives at build
# time (so symlinks exist in the image) and mount /var, /tmp, /run as tmpfs
# instead of trying to write the read-only root at boot.
IMAGE_FEATURES += "read-only-rootfs"

IMAGE_INSTALL = "\
    packagegroup-core-boot \
    libubootenv-bin \
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

# The runtime root is a read-only squashfs. The LCI runtime keeps writable state
# on separate UBI volumes (config -> /etc/natinst/share, cal -> /mnt/cal); their
# mountpoints must already exist in the image so the mount scripts don't try to
# mkdir on the read-only root.
lci_prepare_readonly_rootfs() {
    install -d ${IMAGE_ROOTFS}${sysconfdir}/natinst/share
    install -d ${IMAGE_ROOTFS}/mnt/cal
}
ROOTFS_POSTPROCESS_COMMAND += "lci_prepare_readonly_rootfs;"

# The nirtcfg and libnitargetcfg postinsts can only run on the target, and
# VB-8034 needs nothing they do. On the read-only root they would rerun every
# boot, so mark them configured instead.
lci_skip_target_postinsts() {
    for pkg in nirtcfg libnitargetcfg; do
        sed -i "/^Package: $pkg\$/,/^\$/s/^\(Status: install [a-z]*\) unpacked\$/\1 installed/" \
            ${IMAGE_ROOTFS}${OPKGLIBDIR}/opkg/status
    done
}
ROOTFS_POSTPROCESS_COMMAND += "lci_skip_target_postinsts;"

# Nothing is deferred to the target. Removing run-postinsts offline would leave
# its systemd enable link dangling, so don't install it.
ROOTFS_BOOTSTRAP_INSTALL = ""

# busybox has no login, su or passwd applets, so keep shadow's.
ROOTFS_RO_UNNEEDED:remove = "shadow"

# LCI runtime users/groups. Legacy creates these and the config/cal UBI volumes
# hold files owned by them (lvuser:ni, webserv:niwscerts); the LCI feed packages
# don't create them, so define them here to match the legacy UIDs/GIDs.
inherit extrausers
EXTRA_USERS_PARAMS = "\
    groupadd -g 1000 ni; \
    groupadd -g 1001 niwscerts; \
    useradd -u 1000 -g ni -G niwscerts -d /home/lvuser -s /bin/sh -M lvuser; \
    useradd -u 1001 -g ni -G niwscerts -d /home/webserv -s /bin/sh -M webserv; \
"

# The wired port must be eth0, the legacy name; drop predictable interface
# naming (eudev's rule, systemd-udevd's default link policy).
lci_wired_eth0() {
    rm -f ${IMAGE_ROOTFS}${nonarch_base_libdir}/udev/rules.d/80-net-name-slot.rules
    rm -f ${IMAGE_ROOTFS}${nonarch_libdir}/udev/rules.d/80-net-name-slot.rules
    if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
        install -d ${IMAGE_ROOTFS}${sysconfdir}/systemd/network
        ln -sf /dev/null ${IMAGE_ROOTFS}${sysconfdir}/systemd/network/99-default.link
    fi
}
ROOTFS_POSTPROCESS_COMMAND += "lci_wired_eth0;"

# libnitargetcfg (GetTargetID) execs the legacy /sbin/fw_printenv path.
lci_fw_env_tool_links() {
    ln -sf ${bindir}/fw_printenv ${IMAGE_ROOTFS}${base_sbindir}/fw_printenv
    ln -sf ${bindir}/fw_setenv ${IMAGE_ROOTFS}${base_sbindir}/fw_setenv
}
ROOTFS_POSTPROCESS_COMMAND += "lci_fw_env_tool_links;"

# The kernel and BOOT.bin boot from the bootfs volume, and the gadget serves the
# SFP CD-ROM from the sfp1/sfp2 partition; the root copies are never read.
lci_drop_unused_boot_payloads() {
    rm -rf ${IMAGE_ROOTFS}/boot/*
    rm -f ${IMAGE_ROOTFS}/usr/local/natinst/share/lci/sfp.iso ${IMAGE_ROOTFS}/usr/local/natinst/share/lci/sfp.iso.sig
}
ROOTFS_POSTPROCESS_COMMAND += "lci_drop_unused_boot_payloads;"

# --- LCI update bundle -------------------------------------------------------
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
#  - LCI_SIGN_METHOD=none (default): unsigned; not device-acceptable.
# The rootfs public key at /etc/natinst/lci.pem must match whichever key signs.
# CI/PR builds (NILE_BUILDING_IN_CI=1) default to signing with the production
# 'lci' key; local developer builds default to unsigned.
LCI_SIGN_METHOD ?= "${@'linuxsigning' if d.getVar('NILE_BUILDING_IN_CI') == '1' else 'none'}"
# Whether a signing failure is fatal. Official/CI builds set 1 (never emit an
# unsigned official bundle); PR builds leave 0 (signing outage doesn't red-wall
# validation -- the bundle is just left unsigned).
LCI_SIGN_REQUIRED ?= "0"
LCI_SIGN_TOOL ?= "ssh -oBatchMode=yes -oConnectTimeout=30 mrsign@linux.signing.ni.systems --"
LCI_SIGN_KEY ?= "lci"
LCI_SIGN_DIGEST ?= "sha256"

do_bundle[depends] += "lci-fitimage:do_deploy"
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
    install -m 0644 "${STAGING_DATADIR}/lci-legacy-sfp/sfp.iso" "${work}/sfp.iso"

    # The updater ubiupdatevol's these with -s <size>; they must be 2048-aligned.
    # (awk, not expr: expr exits 1 when the remainder is 0, tripping set -e.)
    for f in root.squashfs sfp.iso; do
        sz=`stat -c%s "${work}/${f}"`
        rem=`awk "BEGIN{print ${sz}%2048}"`
        if [ "${rem}" != "0" ]; then
            bbfatal "${f} size (${sz}) is not a multiple of 2048 bytes"
        fi
        if [ "${f}" = "sfp.iso" ] && [ "${sz}" -lt 614400 ]; then
            bbfatal "${f} size (${sz}) is below the 300-sector CD-ROM minimum"
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
      *)
        bbwarn "LCI_SIGN_METHOD=none: ${LCI_BUNDLE_NAME} is UNSIGNED. Set 'linuxsigning' (production 'lci' key) for a device-acceptable bundle."
        ;;
    esac

    tar -czf "${DEPLOY_DIR_IMAGE}/${LCI_BUNDLE_NAME}" --owner=root --group=root -C "${work}" .
    install -m 0644 "${work}/firmware.info" "${DEPLOY_DIR_IMAGE}/firmware.info"
}
addtask bundle after do_image_complete before do_build
