***INSERT GRAPHIC HERE (include hyperlink in image)***
# Funker

![alt text](https://www.dibiasi.nl/img/funker.png "Funker")

> Easy to use bluetooth library for RFCOMM and OBEX on Android. Powered by Rx!


[![](https://jitpack.io/v/ddibiasi/Funker.svg)](https://jitpack.io/#ddibiasi/Funker)


---

## Features
- Find bluetooth devices
- Send & listen for RFCOMM commands
- OBEX file transfer

## Examples

Search for bluetooth devices
```kotlin
finder
    .search(checkPaired = true, indefinite = true)
    .distinct()
    .subscribeBy(
        onNext = { device ->
            Log.d(TAG, "Found device: ")
        },
        onError = { it.printStackTrace() },
        onComplete = { Log.d(TAG, "Completed search") }
    )
```

Send files via OBEX
```kotlin
rxOBEX
    .putFile("rubberduck.txt", "text/plain", "oh hi mark".toByteArray(), "test")
    .subscribeBy(
        onComplete = {
            Log.d(TAG, "Succesfully sent a testfile to ${device.address}")
        },
        onError = { e ->
            Log.e(TAG, "Received error!")
        })
    
```

Send RFCOMM commands
```kotlin
rxSpp
    .send(command)
    .subscribeBy(
        onComplete = {
            Log.d(TAG, "Succesfully sent $command to ${device.address}")
        },
        onError = { e ->
            Log.e(TAG, "Received error!")
        })
        
```

---

## Setup

- Add JitPack to your root build.gradle

```
allprojects {
 		repositories {
 			...
 			maven { url 'https://jitpack.io' }
 		}
 	}
```
- Add Funker to your project level build.gradle
```
	dependencies {
	        implementation 'com.github.ddibiasi:Funker:0.0.x'
	}
```


## Usage
- tbd

---

## License

[![License](http://img.shields.io/:license-mit-blue.svg?style=flat-square)](http://badges.mit-license.org)