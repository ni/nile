# Image class for images enforcing a set-on-first-use policy

# root user must change password at first login
inherit extrausers
EXTRA_USERS_PARAMS += "passwd-expire root;"

# root user starts off with empty password
IMAGE_FEATURES:append = " empty-root-password allow-empty-password allow-root-login"

# Unfortunately, testimage is very reliant on generated images not having passwords.
# Warn if we try to enable SOFU when building with testimage.
python () {
    if bb.utils.contains('IMAGE_CLASSES', 'testimage', True, False, d):
        bb.warnonce("SOFU is not presently compatible with the the testimage test runner. Test runs with this image will fail.")
}
