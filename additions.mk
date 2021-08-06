# Generic overlays
PRODUCT_PACKAGE_OVERLAYS += device/generic/car/common/overlay device/snappautomotive/common/overlay

# Add non-public overlays if they exist
$(call inherit-product-if-exists, vendor/snappautomotive/non_public/additions.mk)

# Snapp Maps
PRODUCT_PACKAGES += osmdroid

# Boot Animation
PRODUCT_COPY_FILES += \
    device/snappautomotive/common/bootanimations/bootanimation.zip:system/media/bootanimation.zip
