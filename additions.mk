# Generic overlays
PRODUCT_PACKAGE_OVERLAYS += device/snappautomotive/common/overlay

# Add non-public overlays if they exist
$(call inherit-product-if-exists, vendor/snappautomotive/non_public/additions.mk)

# Snapp Maps
PRODUCT_PACKAGES += osmdroid

# Boot Animation
OVERRIDE_BOOT_ANIMATION=true
PRODUCT_COPY_FILES += \
    device/snappautomotive/common/bootanimations/bootanimation.zip:system/media/bootanimation.zip
