# Generic overlays
PRODUCT_PACKAGE_OVERLAYS += device/generic/car/common/overlay

# Snapp Maps
PRODUCT_PACKAGES += SnappMaps

# Boot Animation
PRODUCT_COPY_FILES += \
    device/snappautomotive/common/bootanimations/bootanimation.zip:system/media/bootanimation.zip
