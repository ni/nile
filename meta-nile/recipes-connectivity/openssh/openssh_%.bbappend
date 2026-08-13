FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

do_install:append() {
	# With password authentication, OpenSSH will not correctly indicate to
	# clients that a password change is required via PASSWD_CHANGEREQ,
	# it instead "succeeds" but dumps the user into the passwd program,
	# for architectural reasons. See:
	#
	# https://marc.info/?l=openssh-unix-dev&m=153818042529792&w=2
	#
	# This makes it very difficult for clients to detect a password-
	# change requirement without screen-scraping for password prompts.
	# Instead, prefer use of KbdInteractive auth (newer OpenSSH calls
	# this ChallengeResponse), which makes the prompts explicit.
	for config in sshd_config sshd_config_readonly; do
		if [ -e ${D}${sysconfdir}/ssh/$config ]; then
			sed -e 's|^[#[:space:]]*KbdInteractiveAuthentication .*|KbdInteractiveAuthentication yes|' \
				-e 's|^[#[:space:]]*ChallengeResponseAuthentication .*|ChallengeResponseAuthentication yes|' \
				-e 's|^[#[:space:]]*PasswordAuthentication .*|PasswordAuthentication no|' \
				-i ${D}${sysconfdir}/ssh/$config
		fi
	done
}
