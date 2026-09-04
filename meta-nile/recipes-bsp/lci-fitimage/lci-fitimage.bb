SUMMARY = "VB-8034 legacy-compatible FIT boot image (lci-kernel.itb) and bootfs UBI volume"
DESCRIPTION = "Legacy-compatible FIT (raw kernel Image, ni-lci DTB, boot \
script, FPGA bitstream) and the bootfs.ubifs volume that carries it as lci0.itb."
LICENSE = "CLOSED"

COMPATIBLE_MACHINE = "vb8034"

SRC_URI = "file://bootscript.txt"

DEPENDS = "u-boot-tools-native mtd-utils-native virtual/kernel lci-boot-inputs"

# The raw kernel Image and the DTB come from the kernel's deploy step.
do_compile[depends] += "virtual/kernel:do_deploy"

inherit deploy

S = "${WORKDIR}"
PACKAGES = ""
EXCLUDE_FROM_WORLD = "1"

# Version string embedded in the FIT metadata (cosmetic).
LCI_FW_VERSION ?= "${DISTRO_VERSION}"

# FPGA bitstream embedded in the FIT (VB2Master synth bit.bin, distinct from the
# FSBL bootgen bitstream), staged from the ni-lci feed's lci-boot-inputs IPK.
LCI_FPGA_BITBIN ?= "${STAGING_DATADIR}/lci-boot-inputs/vb8034_master_synth.bit.bin"

# LCI NAND UBI geometry (min I/O 2KB, LEB 126KB = 128K PEB - 2K, <=250 LEBs).
LCI_UBIFS_MIN_IO ?= "2048"
LCI_UBIFS_LEB ?= "129024"
LCI_UBIFS_MAX_LEB ?= "250"

do_configure[noexec] = "1"

do_compile() {
    local fit="${WORKDIR}/fit"
    rm -rf "${fit}"
    install -d "${fit}"

    # FIT payloads, referenced by relative path from the ITS.
    install -m 0644 "${DEPLOY_DIR_IMAGE}/Image" "${fit}/Image"
    install -m 0644 "${DEPLOY_DIR_IMAGE}/${KERNEL_DEVICETREE}" "${fit}/ni-lci.dtb"
    install -m 0644 "${WORKDIR}/bootscript.txt" "${fit}/bootscript.txt"

    if [ ! -f "${LCI_FPGA_BITBIN}" ]; then
        bbfatal "LCI_FPGA_BITBIN not found: ${LCI_FPGA_BITBIN} (staged by the lci-boot-inputs recipe from the ni-lci feed)."
    fi
    install -m 0644 "${LCI_FPGA_BITBIN}" "${fit}/lcibit.bit.bin"

    # ITS reproducing the legacy generate-its.py output (single config,
    # kernel+fdt+bootscript+fpga, USB gadget properties for VB-8034).
    cat > "${fit}/lci.its" <<EOF
/dts-v1/;

/ {
    description = "VB-8034 firmware";
    version = "${LCI_FW_VERSION}";
    #address-cells = <1>;

    images {
        kernel@1 {
            description = "Linux kernel";
            version = "${LCI_FW_VERSION}";
            data = /incbin/("./Image");
            type = "kernel";
            arch = "arm";
            os = "linux";
            compression = "none";
            load = <0x00008000>;
            entry = <0x00008000>;
            hash@1 { algo = "crc32"; };
        };
        fdt@1 {
            description = "device tree";
            data = /incbin/("./ni-lci.dtb");
            type = "flat_dt";
            arch = "arm";
            compression = "none";
            hash@1 { algo = "crc32"; };
        };
        bootscript@1 {
            description = "boot script";
            data = /incbin/("./bootscript.txt");
            type = "script";
            arch = "arm";
            compression = "none";
            hash@1 { algo = "crc32"; };
        };
        fpga@1 {
            description = "FPGA image";
            data = /incbin/("./lcibit.bit.bin");
            type = "firmware";
            arch = "arm";
            compression = "none";
            hash@1 { algo = "crc32"; };
        };
    };

    configurations {
        default = "config@1";
        config@1 {
            description = "default";
            kernel = "kernel@1";
            fdt = "fdt@1";
            fpga = "fpga@1";
            gadget-cdrom = <1>;
            gadget-rndis = <0>;
            gadget-manufacturer = "National Instruments";
        };
    };
};
EOF

    ( cd "${fit}" && uboot-mkimage -f lci.its "${WORKDIR}/lci-kernel.itb" )

    # bootfs UBI volume: carries the FIT as the lci0.itb boot slot.
    local bootfs="${WORKDIR}/bootfs"
    rm -rf "${bootfs}"
    install -d "${bootfs}"
    install -m 0644 "${WORKDIR}/lci-kernel.itb" "${bootfs}/lci0.itb"
    mkfs.ubifs -m ${LCI_UBIFS_MIN_IO} -e ${LCI_UBIFS_LEB} -c ${LCI_UBIFS_MAX_LEB} \
        -r "${bootfs}" "${WORKDIR}/bootfs.ubifs"
}

do_deploy() {
    install -d "${DEPLOYDIR}"
    install -m 0644 "${WORKDIR}/lci-kernel.itb" "${DEPLOYDIR}/lci-kernel.itb"
    install -m 0644 "${WORKDIR}/bootfs.ubifs" "${DEPLOYDIR}/bootfs.ubifs"
}
addtask deploy after do_compile
