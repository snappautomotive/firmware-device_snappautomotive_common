TARGET_USES_CAR_FUTURE_FEATURES := true

# CarServiceHelperService accesses the hidden api in the system server.
SYSTEM_OPTIMIZE_JAVA := false

# Include EVS reference implementations
ENABLE_EVS_SAMPLE := true

# Override heap growth limit due to high display density on device
PRODUCT_PROPERTY_OVERRIDES += dalvik.vm.heapgrowthlimit=256m

# Generic overlays
PRODUCT_PACKAGE_OVERLAYS += device/generic/car/common/overlay device/snappautomotive/common/overlay

# Add non-public overlays if they exist
$(call inherit-product-if-exists, vendor/snappautomotive/non_public/additions.mk)

# Snapp Maps
PRODUCT_PACKAGES += \
	osmdroid \
	aosp-template-host

# Boot Animation
OVERRIDE_BOOT_ANIMATION=true
PRODUCT_COPY_FILES += \
    device/snappautomotive/common/bootanimations/bootanimation.zip:system/media/bootanimation.zip
