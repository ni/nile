# Image upgrades and rollbacks

When using RAUC, we need to support upgrade, downgrade, and rollback of user data
(which includes an overlay on top of /).

User data is manipulated in a couple ways:

- On upgrade:
    - We want to preserve user changes to `/etc` and `/var` to maintain configuration.
        - In general, most software is able to read configuration from older versions of itself.
    - We want to remove all user changes to `/usr`, because we want to ensure we're using the newer software in the base image.
    - Other directories that are not covered by content in the base image (such as `/root` and `/home`) should be left alone.
- On downgrade:
    - We want to remove most user changes to `/etc`, and all changes to `/var` and `/usr`.
        - We _do_ want to preserve some essential configuration out of `/etc`, like passwords and ssh keys.
        - However, we don't know what software inside the image is able to read configuration from newer versions of itself, so we have to assume that none of it can. :(
    - Again, other directories that are not covered by content in the base image (such as `/root` and `/home`) should be left alone.
- On rollback:
    - If we're rolling back due to a bad image, then we need to unwind any upgrade/downgrade-related changes that would have happened as above.

We make use of btrfs and snapshots to do this.

The main user data partition has a few btrfs subvolumes on it. These are prefixed by `@` as a convention to describe what directory they represent (the `@` replaces a `/`).
- `@`: root directory for the overlay (which will be `/`), but with subdirectories for `rofs`, `upper`, and `work` as required by overlayfs
- `@home`: mounted as `/home`
- `@root`: mounted as `/root`
- `@srv`: mounted as `/srv`
- more subvolumes could be added for stuff that we want to perserve across upgrades/downgrades
    - a potential option could be /var/log if we want logs to persist on flash

The high-level algorithm that we use here is essentially:

- From the initramfs, we check to see if the image we're booting has changed from last boot, and if so if it's an upgrade or a downgrade.
- If it has changed:
    - if no snapshot currently exists, then we take a btrfs snapshot of the overlay state before doing any user data changes (as noted above)
    - if a snapshot does exist and it corresponds to the current rootfs, then we must be rolling back from a failed upgrade and so we restore the snapshot.

## test procedure

Note: some aspects of this test procedure are based around the state of the
NILE trunk at the time of development, and also require manual
instrumentation for a "bad" build.

I tested this by making four build versions:

```yml
# ver-1.0.yml
header:
  version: 19
env:
  BUILDNAME: "1.0.0d0"
```

```yml
# ver-2.0.yml
header:
  version: 19
env:
  BUILDNAME: "2.0.0d0"
```

```yml
# ver-3.0.yml
header:
  version: 19
env:
  BUILDNAME: "3.0.0d0"
```

```yml
# ver-4.0.yml
header:
  version: 19
env:
  BUILDNAME: "4.0.0d0"
```

I added some manual instrumentation to nile-overlay.sh to check whether the ${OLDROOT}/usr/lib/os-release version is 3.0.0d0, and then to force a crash (via `echo 1 > /proc/sys/kernel/panic; echo c > /proc/sysrq-trigger`) after having created the snapshot but before we switch into the new overlay. This simulates the "we crashed sometime before we got to the `rauc mark-good` stage" experience.

I then built the images and keep the raucbs. Note that "2" is last here, because that's the thing we want to boot into first:
```sh
$ for i in "4 3 1 2"; do ./kas-container build kas/targets/nile-image-dev_kula.yml:ver-$i.0.yml; cp build/tmp/deploy/images/kula/update-bundle-kula.raucb ~/tmp/update-bundle-kula-$i.0.raucb; done
```

Then, run the VM. Note that kula VMs don't currently have a network interface that QEMU understands, so we have QEMU attach an xHCI one. (AB#3780821)
```sh
$ ./kas-runqemu kas/targets/nile-image-dev_kula.yml slirp nographic qemuparams="-netdev user,id=n1 -device usb-net,netdev=n1"
```

We're now in the "2.0" image (note: the banner before the login prompt is still "1.0" because that's not tied
to BUILDNAME, look at IMAGE_VERSION in /etc/os-release).

```sh
root@kula:~# passwd      (and then set a password)
root@kula:~# # now let's test the upgrade case:
root@kula:~# touch /home/should-be-kept.txt /usr/should-be-cleared.txt /etc/should-be-kept.txt
root@kula:~# md5sum /etc/ssh/ssh_host_ed25519_key
f1acdf2df97c18b7c22f64faf9e19a77  /etc/ssh/ssh_host_ed25519_key
root@kula:~# scp <user>@192.168.7.1:tmp/update-bundle-kula-4.0.raucb /tmp
root@kula:~# rauc install /tmp/update-bundle-kula-4.0.raucb
root@kula:~# reboot
```

After the bootup, I do need to login with the password I set (so that was preserved), and the files
are kept/cleared as expected:

```sh
root@kula:~# ls /home/should-be-kept.txt /usr/should-be-cleared.txt /etc/should-be-cleared.txt
ls: cannot access '/usr/should-be-cleared.txt': No such file or directory
ls: cannot access '/etc/should-be-cleared.txt': No such file or directory
/home/should-be-kept.txt
root@kula:~# md5sum /etc/ssh/ssh_host_ed25519_key
f1acdf2df97c18b7c22f64faf9e19a77  /etc/ssh/ssh_host_ed25519_key
# now let's test the downgrade case:
root@kula:~# touch /home/should-be-kept.txt /usr/should-be-cleared.txt /etc/should-be-cleared.txt
root@kula:~# scp <user>@192.168.7.1:tmp/update-bundle-kula-1.0.raucb /tmp
root@kula:~# rauc install /tmp/update-bundle-kula-1.0.raucb
root@kula:~# reboot
```

After the bootup, I again need to login with the password I set (so that was preserved), and the files are
kept/cleared as expected:

```sh
root@kula:~# ls /home/should-be-kept.txt /usr/should-be-cleared.txt /etc/should-be-cleared.txt
ls: cannot access '/usr/should-be-cleared.txt': No such file or directory
ls: cannot access '/etc/should-be-cleared.txt': No such file or directory
/home/should-be-kept.txt
root@kula:~# md5sum /etc/ssh/ssh_host_ed25519_key
f1acdf2df97c18b7c22f64faf9e19a77  /etc/ssh/ssh_host_ed25519_key
# now let's test the "upgrade to a bad image" case:
root@kula:~# touch /home/should-be-kept.txt /usr/should-be-kept.txt /etc/should-be-kept.txt
root@kula:~# scp <user>@192.168.7.1:tmp/update-bundle-kula-3.0.raucb /tmp
root@kula:~# rauc install /tmp/update-bundle-kula-3.0.raucb
root@kula:~# reboot
```

This one takes a while because we have to boot-fail three times..., but eventually we'll end up back in our 1.0 image.
You'll need to log in with the password you set (so your password didn't change) and the files are unmodified.

```sh
root@kula:~# ls /home/should-be-kept.txt /usr/should-be-kept.txt /etc/should-be-kept.txt
/etc/should-be-kept.txt  /home/should-be-kept.txt  /usr/should-be-kept.txt
root@kula:~# md5sum /etc/ssh/ssh_host_ed25519_key
f1acdf2df97c18b7c22f64faf9e19a77  /etc/ssh/ssh_host_ed25519_key
```

## caveats

While rolling back is intended to restore the previous state of the system, the fact that we've restored from a snapshot means that the btrfs subvolume is a different one (different UUID and creation time)
than the one that we started from, so there does exist an observable state change.
