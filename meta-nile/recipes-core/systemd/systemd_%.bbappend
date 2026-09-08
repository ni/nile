# meta-xilinx-core ships 0001-socket-downgrade-not-supported-logging-for-SO_PASSSE.patch
# for systemd, but the change is already present in the pinned OE-core systemd
# (255.22): the patch fails to apply forward ("can be reverse-applied") and
# breaks do_patch on any from-source build -- i.e. clean/CI builds with cold
# sstate. Drop the redundant patch. Revisit if meta-xilinx is bumped.
SRC_URI:remove = "file://0001-socket-downgrade-not-supported-logging-for-SO_PASSSE.patch"
