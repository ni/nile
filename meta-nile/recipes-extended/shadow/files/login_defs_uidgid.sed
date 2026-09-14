s@^\(SYS_UID_MIN\s*\)\(.*\)$@\1600@g
s@^\(SYS_UID_MAX\s*\)\(.*\)$@\1999@g
s@^\(SYS_GID_MIN\s*\)\(.*\)$@\1600@g
s@^\(SYS_GID_MAX\s*\)\(.*\)$@\1999@g
s@^\(UID_MIN\s*\)\(.*\)$@\11000@g
s@^\(UID_MAX\s*\)\(.*\)$@\119999@g
s@^\(GID_MIN\s*\)\(.*\)$@\11000@g
s@^\(GID_MAX\s*\)\(.*\)$@\119999@g
