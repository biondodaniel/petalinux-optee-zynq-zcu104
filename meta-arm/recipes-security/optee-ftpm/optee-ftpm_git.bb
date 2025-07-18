SUMMARY = "OPTEE fTPM Microsoft TA"
DESCRIPTION = "TCG reference implementation of the TPM 2.0 Specification."
HOMEPAGE = "https://github.com/torsec/ms-tpm-20-ref/"

COMPATIBLE_MACHINE ?= "invalid"
COMPATIBLE_MACHINE:genericarm64 = "genericarm64"
COMPATIBLE_MACHINE:qemuarm64 = "qemuarm64"
COMPATIBLE_MACHINE:qemuarm64-secureboot = "qemuarm64"
COMPATIBLE_MACHINE:qemuarm-secureboot = "qemuarm"

#FIXME - doesn't currently work with clang
TOOLCHAIN = "gcc"

inherit deploy python3native

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=5a3925ece0806073ae9ebbb08ff6f11e"

DEPENDS = "python3-pyelftools-native optee-os-tadevkit python3-cryptography-native liboqs"

FTPM_UUID = "bc50d971-d4c9-42c4-82cb-343fb7f37896"

SRC_URI = "gitsm://github.com/torsec/ms-tpm-20-ref;branch=pq-ftpm;protocol=https \
           "
SRCREV = "18413b06ef0d7f567492cd6fdd507e3e11670a44"

# no pq - only measured-boot
# SRC_URI = "gitsm://github.com/torsec/ms-tpm-20-ref;branch=measured-boot;protocol=https \
#            file://0001-add-enum-to-ta-flags.patch \
#            "
# SRCREV = "eca1d44094c818c46ab97fbb768a3439d032a40a"

# pq ms-tpm-20-ref - updated version
# SRC_URI = "gitsm://github.com/biondodaniel/ms-tpm-20-ref;branch=pq-measured;protocol=https \
#            "
# SRCREV = "7ef3dcb93a2fb1f4065a846202dd418fa360c641"

UPSTREAM_CHECK_COMMITS = "1"

S = "${WORKDIR}/git"

OPTEE_CLIENT_EXPORT = "${STAGING_DIR_HOST}${prefix}"
TEEC_EXPORT = "${STAGING_DIR_HOST}${prefix}"
TA_DEV_KIT_DIR = "${STAGING_INCDIR}/optee/export-user_ta"

EXTRA_OEMAKE += '\
    CFG_FTPM_USE_WOLF=y \
    TA_DEV_KIT_DIR=${TA_DEV_KIT_DIR} \
    TA_CROSS_COMPILE=${TARGET_PREFIX} \
    CFLAGS="${CFLAGS} --sysroot=${STAGING_DIR_HOST} -I${WORKDIR}/optee-os" \
'

#enable measured boot ftpm
EXTRA_OEMAKE += '\
    MEASURED_BOOT=y \
    MEASURED_BOOT_FTPM=y \
    CFG_TA_MEASURED_BOOT=y \
    CFG_TA_DEBUG=y \
    CFG_TPM_MAX_LOG_SIZE=693 \
    CFG_TPM_LOG_BASE_ADDR=0x79640000 \
'

EXTRA_OEMAKE:append:aarch64:qemuall = "\
    CFG_ARM64_ta_arm64=y \
"

# TODO: GCC 14.1 is finding genuine issues with the code but as upstream appear to be removing
# the code we're building (https://github.com/microsoft/ms-tpm-20-ref/pull/108) lets just
# ignore them for now.
CFLAGS += "-Wno-implicit-function-declaration -Wno-incompatible-pointer-types"

# python3-cryptography needs the legacy provider, so set OPENSSL_MODULES to the
# right path until this is relocated automatically.
export OPENSSL_MODULES = "${STAGING_LIBDIR_NATIVE}/ossl-modules"

PARALLEL_MAKE = ""

do_compile:prepend() {
    mkdir -p ${RECIPE_SYSROOT}/usr/include/optee/export-user_ta/include/oqs
    cp ${RECIPE_SYSROOT}/usr/include/oqs/* ${RECIPE_SYSROOT}/usr/include/optee/export-user_ta/include/oqs/
}

do_compile() {
    # The internal ${CC} includes the correct -mcpu option
    sed -i 's/-mcpu=$(TA_CPU)//' Samples/ARM32-FirmwareTPM/optee_ta/fTPM/sub.mk
    # there's also a secure variable storage TA called authvars
    cd ${S}/Samples/ARM32-FirmwareTPM/optee_ta
    oe_runmake
}

do_install () {
    mkdir -p ${D}/${nonarch_base_libdir}/optee_armtz
    install -D -p -m 0644 ${S}/Samples/ARM32-FirmwareTPM/optee_ta/out/fTPM/${FTPM_UUID}.ta ${D}/${nonarch_base_libdir}/optee_armtz/
    install -D -p -m 0644 ${S}/Samples/ARM32-FirmwareTPM/optee_ta/out/fTPM/${FTPM_UUID}.stripped.elf ${D}/${nonarch_base_libdir}/optee_armtz/
}

do_deploy () {
    install -d ${DEPLOYDIR}/optee
    install -D -p -m 0644 ${S}/Samples/ARM32-FirmwareTPM/optee_ta/out/fTPM/${FTPM_UUID}.stripped.elf ${DEPLOYDIR}/optee/
}

addtask deploy before do_build after do_install

FILES:${PN} += " \
               ${nonarch_base_libdir}/optee_armtz/${FTPM_UUID}.ta \
               ${nonarch_base_libdir}/optee_armtz/${FTPM_UUID}.stripped.elf \
               "

# Imports machine specific configs from staging to build
PACKAGE_ARCH = "${MACHINE_ARCH}"
INSANE_SKIP:${PN} += "ldflags"
