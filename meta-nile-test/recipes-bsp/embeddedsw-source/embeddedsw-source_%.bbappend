FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

COMPATIBLE_MACHINE:genesyszu-3eg = "genesyszu"

SRC_URI:append:genesyszu = " \
	file://0001-Commented-I2C-mux-code-for-Digilent-Genesys-ZU.patch \
	file://0002-fsbl-Reset-usb-phys-and-hub-upon-board-init.patch \
	file://0003-zynqmp_dram_test-Added-board-specific-reference-freq.patch \
	file://0004-Added-Genesys-ZU-to-list-of-boards-supporting-DDR4-d.patch \
	file://0005-zynqmp_fsbl-default-to-debug-level-prints.patch \
	"
