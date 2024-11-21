# Generic overlays
PRODUCT_PACKAGE_OVERLAYS += device/snappautomotive/common/overlay

ifndef BUILD_VARIANT
  BUILD_VARIANT := snappautomotive
endif

# Add non-public overlays if they exist
$(call inherit-product-if-exists, vendor/snappautomotive/non_public/additions.mk)

# Snapp Provided Packages
PRODUCT_PACKAGES += \
	CarServiceOverlay \
	NoUICarProvision \
	osmdroid \
	aosp-template-host

# Allow 10s for the Emulator VHAL to respond to the car watchdog
# Ref: https://android.googlesource.com/device/generic/car/+/4ca4eaed9e46047c306698d65deb1f6bfad9dfa7%5E%21/#F0
PRODUCT_PRODUCT_PROPERTIES += \
    ro.carwatchdog.vhal_healthcheck.interval=10

# Boot Animation
OVERRIDE_BOOT_ANIMATION=true
PRODUCT_COPY_FILES += \
    device/snappautomotive/common/bootanimations/bootanimation.zip:system/media/bootanimation.zip
