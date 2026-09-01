# User Management

## Set-on-first-use Administrative Passwords

NILE images should employ a set-on-first-use password strategy, for images
where login can occur. This can be enabled in OpenEmbedded with the following:

```
# root user must change password at first login
inherit extrausers
EXTRA_USERS_PARAMS += "passwd-expire root;"
```

This forces a root user to set a password at first login over both serial
and ssh.

```
NI Linux Embedded 1.0 kula ttyAMA0

kula login: root
You are required to change your password immediately (administrator enforced).
New password:
Retype new password:
```

When a password is changed, the change is applied to `/etc/shadow`, which is
written to the user data overlay partition (see [disk-layout.md]). Since we
reuse the overlay between A/B images, this mechanism preserves the password
between them.

### When not to use set-on-first-use

Certain OpenEmbedded testing scenarios currently require an empty non-expired
root password (the OpenEmbedded `testimage` runner, for example, does not know
how to do login otherwise).

Additionally, if you ship an image that does not have login capability
available (neither over ssh nor serial) then it is not necessary to expire a
root password that can never be set.

## Factory reset behavior

On factory reset, the user data overlay partition is erased. This also
erases any changes to /etc/shadow, restoring the "root password is expired"
state.

## User and group IDs

We must statically define all user and group IDs in the distro in order to
provide deterministic build behavior as well as to ensure that firmware
upgrades do not conflict with on-target user and group additions. This is
not a unique requirement; [ChromiumOS makes UID/GID declarations][CROS-USERS],
and we have [past experience with NI Linux RT][NILRT-324] on the
ramifications of such conflicts.

### Allocating a new static system UID or GID

Pick the next-lowest number in the range 200..499.

Every UID should have a GID with the same value.

### Deallocating a static system UID or GID

UID/GIDs may not be deallocated. If it is no longer needed, the reservation
must remain in order to prevent the ID from being reused.

### Dynamic user and group IDs

The `/etc/login.defs` file governs what ranges are used when `adduser` and
friends are executed on-target. We define them as:

- `SYS_UID_{MIN,MAX}` - 600 to 999
- `SYS_GID_{MIN,MAX}` - 600 to 999
- `UID_{MIN,MAX}` - 1000 to 19999
- `GID_{MIN,MAX}` - 1000 to 19999

Note that new system UID/GIDs will use the first free value less than
SYS_xID_MAX, and new normal UID/GIDs will use the first free value greater
than xID_MIN.

### Ranges

| UID/GID range | purpose                                           |
|---------------|---------------------------------------------------|
| 0..199        | base-passwd                                       |
| 200..499      | static system reservations by OE core layers      |
| 500..549      | NI external IPKs (lvuser, webserv, ni)            |
| 550..599      | static system reservations by NILE product layers |
| 600..999      | dynamic system uid/gid                            |
| 1000..19999   | dynamic normal uid/gid                            |
| 20000..65533  | reserved for future use                           |
| 65534         | base-passwd (nobody:nogroup)                      |

### Device upgrades

When a rootfs image is upgraded, we must merge any users and groups that may
be newly-present in the rootfs /etc/passwd and /etc/group with the
/etc/passwd and /etc/group in the user data partition that is applied
as an overlay, because the user may have created new users and groups
implicitly via package installs or explicitly via `adduser`.

Due to our choice of ranges, these should not conflict.

### Other Technical Considerations

This relies on OpenEmbedded's `useradd-staticids.bbclass`.

OpenEmbedded does provide "static-{group,passwd}-" files for
`meta-openembedded`, but only for that layer, and without a proper central
registry for such files ensuring that they do not conflict between different
layers. Therefore, we must maintain our own database for NILE.

OpenEmbedded uses (and augments) `base-passwd` from Debian, a mechanism that
Debian upstream [was very surprised by and do not support][DEB-BP-MR-17].
Debian is moving in the direction of using [systemd-sysusers][SD-SYSUSERS].


[CROS-USERS]: https://www.chromium.org/chromium-os/developer-library/reference/build/account-management/
[NILRT-324]: https://github.com/ni/meta-nilrt/pull/324
[DEB-BP-MR-17]: https://salsa.debian.org/debian/base-passwd/-/merge_requests/17
[SD-SYSUSERS]: https://www.freedesktop.org/software/systemd/man/latest/systemd-sysusers.html
