# Keep the SSH host keys on the config volume, which populateconfig creates.
do_install:append:vb8034() {
	sed -i 's|^HostKey /var/run/ssh/|HostKey ${sysconfdir}/natinst/share/ssh/|' \
		${D}${sysconfdir}/ssh/sshd_config_readonly
}
