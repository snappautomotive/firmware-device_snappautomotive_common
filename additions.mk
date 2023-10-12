# Generic overlays
PRODUCT_PACKAGE_OVERLAYS := device/snappautomotive/common/overlay

# Add non-public overlays if they exist
$(call inherit-product-if-exists, vendor/snappautomotive/non_public/additions.mk)

# Snapp Provided Packages
PRODUCT_PACKAGES += \
	CarServiceOverlay \
	osmdroid \
	aosp-template-host

# Boot Animation
PRODUCT_COPY_FILES += \
    device/snappautomotive/common/bootanimations/bootanimation.zip:system/media/bootanimation.zip
