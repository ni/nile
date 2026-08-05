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
# mount point for read-only rootfs (base image), used as lowerdir
ROMOUNT="${RWMOUNT}/rofs"
# upper directory (read-write data location)
UPPER_DIR="${RWMOUNT}/upper"
# overlayfs work directory (must be on same fs as upperdir)
WORK_DIR="${RWMOUNT}/work"

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
