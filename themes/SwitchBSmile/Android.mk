LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_RRO_THEME := BlueSmile
LOCAL_PRODUCT_MODULE := true
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
LOCAL_PACKAGE_NAME := BlueSmile
LOCAL_SDK_VERSION := current
LOCAL_CERTIFICATE := platform

include $(BUILD_RRO_PACKAGE)
