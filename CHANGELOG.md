# Change Log

## 3.8.4

### Bug Fixes

- Fix NPE when creating a notification channel when receiving a push notification on Android

## 3.8.3

### Bug Fixes

- iOS runtime args service correctly handles local notifications
- implement notification channels introduced in Android API level 26

### Enhancements

- remove vibration feedback when receiving notifications on Android

## 3.8.2

### Bug Fixes

- Fixed issue in Video service for Android with target 24+.

## 3.8.1

### Enhancements

- zxing barscanning library has been updated to 3.3.3
- aztec format is enabled by default in Barcode Scan service

## 3.8.0

Released with Gluon Mobile 5.0.0

### New Features

- Notch API added to DisplayService to support mobiles with notches like iPhone X
- Start, stop methods and custom settings to PositionService
- Return the image file in PicturesService

### Bug Fixes

- Added directive to build on iOS lower than 11

## 3.7.2

### New Features

- Added parameters to Barcode Scan service
  
* Title for the scan view
* Legend for a helper message
* Result text, to display a message when the scan ends

### Bug Fixes

- Fix crash on VideoService on iOS 11
- Apply JNI_OnLoad to iOS native code

## 3.7.1

### Bug Fixes

- Fix service loading in iOS

## 3.7.0

This release brings in support for both Java 8 and 9

### New Features

- DisplayService has a new `getDefaultDimensions()` to return the visual bounds
