# Gluon Charm Down #

Gluon [Charm Down](http://gluonhq.com/products/mobile/charm-down/) is the component that addresses the integration with low-level platform APIs in an end-to-end Java Mobile solution.

Using Charm Down, you write code that access device and hardware features using a uniform, platform-independent API. 
At runtime, the appropriate implementation (charm-down-desktop, charm-down-android, charm-down-ios) makes sure the platform specific code is 
used to deliver the functionality.

Charm Down is open source, and it is freely licensed under the GPL license.
[Gluon](http://gluonhq.com) can provide [custom consultancy](http://gluonhq.com/services/consulting/) and [training](http://gluonhq.com/services/training/), commercial licenses, and open source [commercial support](http://gluonhq.com/services/commercial-support/), including daily and monthly releases.

## Getting started ##

The best way to get started with Gluon Charm Down is using the [Gluon plugin for your IDE](http://gluonhq.com/get-started/ide-plugins/)
and creating a [Gluon Mobile](http://gluonhq.com/products/mobile) project.

The [Gluon samples](http://gluonhq.com/developers/samples/) are a good way to find out how Charm Down is used.

See the [documentation](http://docs.gluonhq.com/charm/latest/#_charm_down) and the 
[Javadoc](http://docs.gluonhq.com/mobile/javadoc/latest/com/gluonhq/charm/down/package-summary.html).

The list of available services at Charm Down can be found [here](http://gluonhq.com/products/mobile/charm-down/).


## Issues and Contributions ##

Issues can be reported to the [Issue tracker](https://bitbucket.org/gluon-oss/charm-down/issues?status=new&status=open)

Contributions can be submitted via [Pull requests](https://bitbucket.org/gluon-oss/charm-down/pull-requests/)


## Building Charm Down ##

### Requisites ###

Gluon Charm Down is frequently released, and this is only required in case you want to fork and build your local version of Charm Down.

These are the requisites:

* A recent version of [JDK 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* Gradle 2.2 or superior. 

To build the Android Services:

* Android SDK: the android sdk must be downloaded. The required Android platform is `android-25`.
* `ANDROID_HOME` has to be defined as an environment variable, typically by adding it as a gradle property to `~/.gradle/gradle.properties`.
* Extras/Android Support Repository and Extras/Google Repository that can be installed with the Android SDK Manager.

To build the iOS Services:
 
* A Mac with with MacOS X 10.11.5 or superior
* XCode 8.x or superior

### How to build and install Charm Down ###

To build the Charm Down services, on the project's root, run:

`./gradlew clean build`

If you want to install them, run:

`./gradlew clean install`

When the process finishes successfully, the different services can be added to a Gluon Mobile project 
by including `mavenLocal()` in the list of repositories.
