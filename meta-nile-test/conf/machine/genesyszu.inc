#@TYPE: Machine
#@NAME: genesyszu
#@DESCRIPTION: Machine support for Digilent Genesys ZU family

#### Preamble
MACHINEOVERRIDES =. "${@['', 'genesyszu:']['genesyszu' !='${MACHINE}']}"
#### Regular settings follow

XILINX_RELEASE_VERSION = "2024.2"

require conf/machine/zynqmp${ZYNQMP_SUBMODEL}-generic.conf

PREFERRED_PROVIDER_virtual/kernel ?= "linux-xlnx"
PREFERRED_VERSION_linux-xlnx = "6.12%"
PREFERRED_VERSION_u-boot-xlnx = "1:2024.01-xilinx-%"
PREFERRED_VERSION_u-boot-tools-xlnx-native = "1:2024.01-xilinx-%"

SPL_BINARY ?= "spl/boot.bin"
UBOOT_ELF = "u-boot"

EXTRA_IMAGEDEPENDS += "\
	u-boot-xlnx-uenv \
	"

IMAGE_BOOT_FILES += "\
	boot.bin \
	uEnv.txt \
	"

### No additional settings should be after the Postamble
#### Postamble
PACKAGE_EXTRA_ARCHS:append = "${@['', ' genesyszu']['genesyszu' != '${MACHINE}']}"
