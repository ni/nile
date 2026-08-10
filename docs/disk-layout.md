# Recommended disk layout for NILE targets

This is the expected disk layout for new targets running NILE.

This layout is not a requirement (especially where we are retrofitting NILE
into existing platforms) but this is what generic NILE infrastructure
expects to see.

## Partition Table (uboot-based targets)

| Label   | Recommended Format | Purpose                    | Comment                            |
|---------|--------------------|----------------------------|------------------------------------|
| uboot   | fat32              | zynq BOOT.IMG (fsbl+uboot) | Required by Zynq, many other SoCs  |
| kernelA | ext4               | kernel image (slot A)      | read-only, managed by rauc         |
| kernelB | ext4               | kernel image (slot B)      | read-only, managed by rauc         |
| rootfsA | ext4 or squashfs   | distro image (slot A)      | read-only, managed by rauc         |
| rootfsB | ext4 or squashfs   | distro image (slot B)      | read-only, managed by rauc         |
| machine | ext4 or btrfs      | machine-specific data      | calibration data, et al. optional. |
| data    | ext4 or btrfs      | user data                  | user state data                    |

### General notes

Aside from the first FAT32 partition (which is architecturally required for
the boot process on Zynq and many other SoCs), no explicit numbering should
be assumed. Partitions should always be referenced by label or by UUID.

Using a GPT partition table is preferable, but nothing precludes
implementation using an MBR partition table.

### uboot

Zynq-based targets require this be a FAT16/32 partition with a BOOT.BIN.

Other SoCs have similar architectural requirements to have a FAT32 partition
be first.

EFI-based systems would have the EFI System Partition in this location.

There may be situations where this partition is absent; for example, a
product where uboot is held in a QSPI flash and the rest of the system on a
user-replacable SD card.

### kernelA/kernelB

Each of these partitions holds a kernel image file, that includes:
- the kernel binary
- hardware device tree(s) if applicable
- initramfs

This can be satisfied with the [Flattened Image Tree][FIT] format or with
[Unified Kernel Images][UKI].

These are kept separate from the rootfs partitions so that the kernel images
can be checked and verified in a boot chain before the rest of the rootfs
data.

We maintain two kernel partitions (and two rootfs partitions) in an A/B
scheme using RAUC.

Each kernel partition needs to be sufficiently large enough to host the
kernel, any modules required for early boot, and an initramfs.

### rootfsA/rootfsB

The partition contents are the contents of the distribution.

The expectation is that these images are not modified during runtime, and
that any changes will take place on a read-write overlay.

Each rootfs partition needs to be sufficiently large enough to host a root
filesystem, and should account for room for growth with new versions of NILE.

### machine

This partition is for machine-specific data that should _not_ be cleared on
a factory reset.

This partition is optional, and how this partition gets used is left up to
product implementations.

For example, calibration data may need to be provisioned only at
manufacturing time and the product is unusable with no calibration data in
place (which means that it should not removed on factory reset). Note that
if data on this partition is modifiable, a product may need to build a
sanitization procedure for that data for statement-of-volatility purposes. 
We declare this out-of-scope for NILE itself.

### data

This partition is for user data. Notably, this partition includes a
directory that is used as a read-write overlay on top of the rootfs. All
configuration and state changes to what is visibly the root directory (e.g.,
`/etc`, `/usr`, etc) actually occur here.

This partition is expected to (but not required to) be last so that it can
fill all remaining disk space.

## Factory Reset and "First Boot State"

When a factory reset occurs, the user data partition should be reformatted
and reinitialized. This reverts any changes to state to what is supplied in
the rootfs image.

The following things are expected to take place:
- any user configuration and data will be erased
- sshd host keys will be regenerated
- /etc/machine-id will be regenerated
- administrative password will be put into set-on-first-login state

Images that utilize systemd >=258 (e.g. starting with OpenEmbedded `wrynose`)
should tie this reformatting in with systemd's [Factory Reset specification][SD-FACTORY]
for consistency with other Linux tooling.

## Additional Technical Considerations

Implementations that can utilize hardware-backed key storage (e.g. TPM)
should encrypt the user data partition.

Implementations that do not require package-management may choose to not
overlay `/usr` (e.g., `/usr` is strictly read-only). If the distro can be
built entirely into a read-only `/usr` (see "hermetic-usr" in
[UAPI.6 Configuration Files Specification][UAPI6]), there is no
distro-supplied `/etc` or `/var` requiring overlay.

## References

Some elements of this layout are inspired by the documentation of
[Flatcar Linux][FCAR-DISK], which uses a similar A/B scheme (itself
inherited from [ChromiumOS][CROS-DISK]).


[FIT]: https://docs.u-boot.org/en/v2024.07/usage/fit/source_file_format.html
[UKI]: https://uapi-group.org/specifications/specs/unified_kernel_image/
[RAUC]: https://rauc.io/
[FCAR-DISK]: https://www.flatcar.org/docs/latest/devguide/sdk-disk-partitions/
[CROS-DISK]: https://www.chromium.org/chromium-os/developer-library/reference/device/disk-format/
[SD-FACTORY]: https://systemd.io/FACTORY_RESET/
[UAPI6]: https://uapi-group.org/specifications/specs/configuration_files_specification/
