# Generic overlays
PRODUCT_PACKAGE_OVERLAYS += device/snappautomotive/common/overlay

ifndef BUILD_VARIANT
  BUILD_VARIANT := snappautomotive
endif

# Add non-public overlays if they exist
$(call inherit-product-if-exists, vendor/$(BUILD_VARIANT)/non_public/additions.mk)

# Snapp Provided Packages
PRODUCT_PACKAGES += \
	CarServiceOverlay \
	osmdroid \
	aosp-template-host

# Boot Animation
OVERRIDE_BOOT_ANIMATION=true
PRODUCT_COPY_FILES += \
    device/snappautomotive/common/bootanimations/bootanimation.zip:system/media/bootanimation.zip
