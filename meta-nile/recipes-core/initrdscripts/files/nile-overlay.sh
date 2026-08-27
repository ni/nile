#!/bin/sh

# SPDX-License-Identifier: MIT
#
# Copyright 2026 (C), National Instruments

# initramfs module intended to mount NILE's read-write /data partition
# as an overlay on top of /, keeping the original rootfs read-only.
#
# This works a bit differently from the `overlayroot` module in the
# initramfs framework:
# - we know what partitions we're looking for, so we do not need
#   additional kernel cmdline options

PATH=/sbin:/bin:/usr/sbin:/usr/bin

# We're running after the "rootfs" module, so rootfs has already been mounted.
# This module is just responsible for the /data partition.

# We get OLDROOT from the rootfs module
OLDROOT="/rootfs"

# mount point for overlay
RWMOUNT="/overlay"
# mount point for new root filesystem
NEWROOT="/root"
# btrfs subvolume
BASEMOUNT="${RWMOUNT}/@"
# mount point for read-only rootfs (base image), used as lowerdir
ROMOUNT="${BASEMOUNT}/rofs"
# upper directory (read-write data location)
UPPER_DIR="${BASEMOUNT}/upper"
# overlayfs work directory (must be on same fs as upperdir)
WORK_DIR="${BASEMOUNT}/work"

update_overlay_version_marker ()
{
	cp "${OLDROOT}/usr/lib/os-release" "${BASEMOUNT}/overlay-release"
}

# Factory reset unmounts the data partition, formats it, then remounts it.
factory_reset () {
	msg "nile-overlay: performing factory reset of user data partition"

	umount "${RWMOUNT}"

	# btrfs mixed mode (-M) is soft-recommended for filesystems smaller than 5GiB.
	info "nile-overlay: reformatting user data partition"
	mkfs.btrfs -q -M -f -L "data" "${data_part_device}" || fatal "nile-overlay: unable to reformat user data partition"

	if mount -n -o rw,sync,relatime "${data_part_device}" "${RWMOUNT}"; then
		info "nile-overlay: remounted user data partition"

		for i in "${BASEMOUNT}" "${RWMOUNT}/@root" "${RWMOUNT}/@home" "${RWMOUNT}/@srv"; do
			btrfs subvolume create "${i}" >/dev/null || fatal "nile-overlay: unable to create btrfs subvolume"
		done

		update_overlay_version_marker
	else
		fatal "nile-overlay: unable to remount user data partition"
	fi
}

handle_overlay_upgrade () {
	msg "nile-overlay: handling rootfs upgrade"

	info "nile-overlay: clearing overlay changes to /usr"
	# clear changes to /usr
	rm -rf "${UPPER_DIR}/usr"

	info "nile-overlay: resetting opkg state"
	# also clear changes opkg state so on-target postinsts get rerun
	rm -rf "${UPPER_DIR}/etc/ipk-postinsts"
	rm -rf "${UPPER_DIR}/var/lib/opkg/status"
}

handle_overlay_downgrade () {
	msg "nile-overlay: handling rootfs downgrade"

	# Create a new upper directory tree with hardlinks to the files that is critical
	# to preserve (password data, ssh keys, etc).

	NEW_UPPER_DIR="${UPPER_DIR}.migrated"
	rm -rf "${NEW_UPPER_DIR}"
	mkdir -p "${NEW_UPPER_DIR}" "${NEW_UPPER_DIR}/etc" "${NEW_UPPER_DIR}/etc/ssh"

	info "nile-overlay: migrating previous configuration data"
	# TODO: packages should install drop-ins somewhere to add to this list
	# rather than having us hardcode it in this initscript
	for i in /etc/passwd /etc/shadow /etc/shadow- /etc/group /etc/gshadow /etc/machine-id \
			/etc/ssh/ssh_host_ecdsa_key /etc/ssh/ssh_host_ecdsa_key.pub \
			/etc/ssh/ssh_host_ed25519_key /etc/ssh/ssh_host_ed25519_key.pub \
			/etc/ssh/ssh_host_rsa_key /etc/ssh/ssh_host_rsa_key.pub; do
		if [ -e "${UPPER_DIR}${i}" ]; then
			ln "${UPPER_DIR}${i}" "${NEW_UPPER_DIR}${i}"
		fi
	done

	# atomic swap new directory for old one, then delete the old data
	info "nile-overlay: using migrated configuration data"
	exch "${NEW_UPPER_DIR}" "${UPPER_DIR}" || fatal "couldn't exchange directories"
	rm -rf "${NEW_UPPER_DIR}"
}

detect_rootfs_change () {
	if [ ! -e "${OLDROOT}/usr/lib/os-release" ]; then
		msg "nile-overlay: WARNING: /usr/lib/os-release not found in rootfs image!"
		return
	fi

	ROOTFS_BUILD=$(grep -s 'BUILD_ID=' "${OLDROOT}/usr/lib/os-release" | sed 's/.*=//;')
	ROOTFS_VER=$(grep -s 'IMAGE_VERSION=' "${OLDROOT}/usr/lib/os-release" | sed 's/.*=//;' | tr -d '"')

	if [ -e "${BASEMOUNT}.old" ]; then
		msg "nile-overlay: found snapshot from upgrade attempt"
		# A saved snapshot exists. Are we booting into the image represented by this?
		# If so, we're reverting back after a failed upgrade.
		SNAPSHOT_BUILD=$(grep -s 'BUILD_ID=' "${BASEMOUNT}.old/overlay-release" | sed 's/.*=//;')

		if [ "${ROOTFS_BUILD}" = "${SNAPSHOT_BUILD}" ]; then
			info "nile-overlay: restoring from snapshot"

			exch "${BASEMOUNT}.old" "${BASEMOUNT}"
			btrfs subvolume delete "${BASEMOUNT}.old" >/dev/null

			# we don't need to do any further upgrade/downgrade checking;
			# we assume that the contents of the snapshot are compatible
			# with the version we're rolling back to.
			return
		else
			# We can't remove the snapshot yet, because we might still
			# have reboot attempts; this needs to be cleaned up _after_
			# we `rauc mark-good`.
			info "nile-overlay: leaving the snapshot alone until we boot successfully"
		fi
	fi

	if [ ! -e "${BASEMOUNT}/overlay-release" ]; then
		# If we don't have the tracking file for what build/version the overlay is
		# associated with, we don't know what version we're coming from.
		msg "nile-overlay: WARNING: overlay does not appear to have an associated version!"
		update_overlay_version_marker
		return
	fi

	OVERLAY_BUILD=$(grep -s 'BUILD_ID=' "${BASEMOUNT}/overlay-release" | sed 's/.*=//;')
	OVERLAY_VER=$(grep -s 'IMAGE_VERSION=' "${BASEMOUNT}/overlay-release" | sed 's/.*=//;' | tr -d '"')

	if [ "${ROOTFS_BUILD}" = "${OVERLAY_BUILD}" ]; then
		# Build id hasn't changed; our user data matched what we last booted
		# with, so there's nothing to do.
		return
	fi

	# Since this is a rootfs change, create a new snapshot before we change anything,
	# so that we can revert back.
	info "nile-overlay: creating snapshot"
	if [ -e "${BASEMOUNT}.new" ]; then
		# If there is a .new snapshot here, that means last time around we died
		# after creating the snapshot but before we exchanged it. Start over.
		info "nile-overlay: removing old lingering snapshot."
		btrfs subvolume delete "${BASEMOUNT}.new" >/dev/null
		sync
	fi
	btrfs subvolume snapshot "${BASEMOUNT}" "${BASEMOUNT}.new" >/dev/null || fatal "nile-overlay: Could not create snapshot of /."
	exch "${BASEMOUNT}" "${BASEMOUNT}.new"
	mv "${BASEMOUNT}.new" "${BASEMOUNT}.old"
	sync

	if [ "${ROOTFS_VER}" = "99.0.0d0" -o "${OVERLAY_VER}" = "99.0.0d0" ]; then
		# versions of "99.0.0d0" are local builds and therefore cannot
		# fit into a linear sequence; ignore upgrade/downgrade checks here.
		# We still want the snapshot available for rollback, though.
		msg "nile-overlay: Refusing to do version comparison on a local development build."
		return
	fi

	systemd-analyze compare-versions "${ROOTFS_VER}" "${OVERLAY_VER}" >/dev/null; CMP=$?
	if [ ${CMP} -eq 11 ]; then
		info "rootfs '${ROOTFS_VER}' > overlay '${OVERLAY_VER}'; we have upgraded"
		handle_overlay_upgrade
	elif [ ${CMP} -eq 12 ]; then
		info "rootfs '${ROOTFS_VER}' < overlay '${OVERLAY_VER}'; we have downgraded"
		handle_overlay_downgrade
	elif [ ${CMP} -eq 0 ]; then
		info "rootfs '${ROOTFS_VER}' == overlay '${OVERLAY_VER}'; same version"
	fi
	update_overlay_version_marker
}

# udev has populated the /dev/disk tree.
# TODO: should probably check to ensure data partition is on same physical volume as rootfs?
#       (protects against someone with an external SD card called "data"...)
# TODO: should we also have a predefined UUID for "data" partition?
if [ -e /dev/disk/by-label/data ]; then
	data_part_device=/dev/disk/by-label/data
elif [ -e /dev/disk/by-partlabel/data ]; then
	data_part_device=/dev/disk/by-partlabel/data
else
	fatal "nile-overlay: unable to find data partition"
fi

mkdir -p ${RWMOUNT}

if mount -n -o rw,sync,relatime "${data_part_device}" "${RWMOUNT}"; then
	info "applying user data partition as overlay"

	# Factory reset
	if [ ! -e "${UPPER_DIR}" ]; then
		info "upper dir missing, this is a first-time boot, performing factory reset"
		factory_reset
	elif [ -e "${UPPER_DIR}/etc/factory-reset" ]; then
		# TODO: The way that we should be doing factory reset should align with systemd
		#       (https://systemd.io/FACTORY_RESET/), but scarthgap's systemd is too old
		#       to have this interface. Current implementation checks for /etc/factory-reset
		#       being present as a "factory reset is requested" signal.

		info "factory reset has been requested"
		factory_reset
	fi

	detect_rootfs_change

	# Set up overlay directories
	mkdir -p "${UPPER_DIR}"
	mkdir -p "${WORK_DIR}"
	mkdir -p "${NEWROOT}"
	mkdir -p "${ROMOUNT}"

	# Remount OLDROOT as read-only
	mount -o bind "${OLDROOT}" "${ROMOUNT}"
	mount -o remount,ro "${ROMOUNT}"

	# Mount RW overlay
	mount -t overlay overlay -o "lowerdir=${ROMOUNT},upperdir=${UPPER_DIR},workdir=${WORK_DIR}" "${NEWROOT}" || fatal "nile-overlay: unable to mount overlay"
else
	fatal "nile-overlay: unable to mount data partition"

	# TODO: if we got to this point, the data partition does exist.
	# On failure here, should we reformat it and try again?
fi

# Set up filesystems on overlay
mkdir -p "${NEWROOT}/proc"
mkdir -p "${NEWROOT}/dev"
mkdir -p "${NEWROOT}/sys"
mkdir -p "${NEWROOT}/rofs"

mount -n --move "${ROMOUNT}" "${NEWROOT}/rofs"
mount -n --move "/proc" "${NEWROOT}/proc"
mount -n --move "/sys" "${NEWROOT}/sys"
mount -n --move "/dev" "${NEWROOT}/dev"

# Remove sync option from data mount in preparation for toggle
sync
mount -o remount,async "$RWMOUNT"

exec chroot "${NEWROOT}/" "${bootparam_init:-/sbin/init}" || fatal "Couldn't chroot into overlay"
