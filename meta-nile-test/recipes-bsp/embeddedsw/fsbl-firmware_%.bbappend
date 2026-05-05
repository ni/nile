COMPATIBLE_MACHINE:genesyszu-3eg = "genesyszu"

# Enable UHS-I speeds for SD
YAML_COMPILER_FLAGS:append:genesyszu = " -DUHS_MODE_ENABLE"
YAML_COMPILER_FLAGS:append:genesyszu = " -DXPS_BOARD_GZU_3EG"

# Workaround erroneous -O2 compilation setting in FSBL
do_configure:append:genesyszu () {
    sed -i "/BSP_FLAGS := -O2 -c/d" ${WORKDIR}/git/fsbl-firmware/fsbl-firmware/Makefile
}
