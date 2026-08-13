# Various packages dynamically add users and groups to the system at package
# install time.  For programs that do not care what the uid/gid is of the
# resulting users/groups, the order of the install will determine the final
# uid/gid.  This can lead to non-deterministic uid/gid values from one build
# to another.  Use the following settings to specify that all user/group adds
# should be created based on a static passwd/group file.
USERADDEXTENSION = "useradd-staticids"
USERADD_TABLE_CATEGORIES = "base openembedded ni-ipk"
USERADD_UID_TABLES = "${@' '.join(['files/static-passwd-%s' % x for x in d.getVar('USERADD_TABLE_CATEGORIES').split()])}"
USERADD_GID_TABLES = "${@' '.join(['files/static-group-%s' % x for x in d.getVar('USERADD_TABLE_CATEGORIES').split()])}"
# In order to prevent generating a system where a dynamicly assigned uid/gid
# can exist, you should enable the following setting.  This will force the
# system to error out if the user/group name is not defined in the static
# group tables
USERADD_ERROR_DYNAMIC = "1"
