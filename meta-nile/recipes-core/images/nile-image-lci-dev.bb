SUMMARY = "LCI dev/service image: nile-image-lci + legacy-matching remote access"
DESCRIPTION = "Development/service variant of nile-image-lci: same LCI runtime \
and update bundle, plus legacy remote access (root/nigel over SSH and a ttyPS0 \
serial login) for bring-up tooling. NOT a production image -- production is \
nile-image-lci, which stays locked."

require nile-image-lci.bb

# Distinct bundle name so a dev .cfg is never confused with the production
# VB-8034.cfg (same device/firmware.info inside; only the outer file differs).
LCI_BUNDLE_NAME = "VB-8034-dev.cfg"

# --- Legacy-matching dev access (verified against VB2-30A1D14, 2026-09-08) ----
# The legacy debug image allows root login over SSH with password 'nigel' and a
# getty on ttyPS0. Reproduce that here so the same tooling keeps working.
#
# 1) root password = 'nigel'. The legacy image stored a DES hash in /etc/passwd
#    (no /etc/shadow); NILE uses /etc/shadow, so this is a SHA-512 hash of the
#    same password. The credential -- root/nigel -- is what must match.
inherit extrausers
EXTRA_USERS_PARAMS = "usermod -p '$6$lcidev$.3jb22FKkJa6Ud8RqqJOLvSqrDHr5/6z0UvyrqETVa4cHTviv8vZTgAwoCCrd8xcK7Ik09sp00p1iQps0MGWz1' root;"

# 2) Enable root login (PermitRootLogin yes).
IMAGE_FEATURES += "allow-root-login"

# 3) Re-enable password auth. The base openssh bbappend
#    (recipes-connectivity/openssh/openssh_%.bbappend) force-writes
#    'PasswordAuthentication no' at package do_install; undo it at rootfs time
#    for this dev image only, and belt-and-suspenders the PermitRootLogin line.
ROOTFS_POSTPROCESS_COMMAND += "lci_dev_enable_password_ssh; "
lci_dev_enable_password_ssh () {
    for config in sshd_config sshd_config_readonly; do
        f="${IMAGE_ROOTFS}${sysconfdir}/ssh/$config"
        [ -e "$f" ] || continue
        sed -i -e 's|^[#[:space:]]*PasswordAuthentication .*|PasswordAuthentication yes|' \
               -e 's|^[#[:space:]]*PermitRootLogin .*|PermitRootLogin yes|' "$f"
        grep -q '^PasswordAuthentication ' "$f" || echo 'PasswordAuthentication yes' >> "$f"
        grep -q '^PermitRootLogin ' "$f" || echo 'PermitRootLogin yes' >> "$f"
    done
}

# Serial login on ttyPS0@115200 is already provided by the machine
# (zynq-generic.conf: SERIAL_CONSOLES = "115200;ttyPS0") under systemd
# (serial-getty@ttyPS0), matching the legacy inittab getty -- no change needed.
