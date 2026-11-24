# The LCI daemon copies from its uncached DMA buffer at unaligned offsets.
# Under systemd, lci-target-config does this instead.
do_install:append:vb8034() {
    sed -i 's|echo "3" > /proc/cpu/alignment|echo "2" > /proc/cpu/alignment|' \
        ${D}${sysconfdir}/init.d/alignment.sh
    grep -q 'echo "2" > /proc/cpu/alignment' ${D}${sysconfdir}/init.d/alignment.sh
}
