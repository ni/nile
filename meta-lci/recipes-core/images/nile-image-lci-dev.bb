require nile-image-lci.bb

SUMMARY = "Development firmware image for LCI"

LCI_BUNDLE_NAME = "VB-8034-dev.cfg"

# Allow root login over SSH with the password 'nigel' (SHA-512 hash below).
inherit extrausers
EXTRA_USERS_PARAMS:append = " usermod -p '\$6\$lcidev\$.3jb22FKkJa6Ud8RqqJOLvSqrDHr5/6z0UvyrqETVa4cHTviv8vZTgAwoCCrd8xcK7Ik09sp00p1iQps0MGWz1' root;"

IMAGE_FEATURES += "allow-root-login"

# meta-nile's openssh bbappend sets 'PasswordAuthentication no'; turn password
# authentication back on for this image.
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
