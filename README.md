# USB Thermal Printer Module for React Native

A complete React Native module for connecting and printing to USB thermal printers on Android devices. Supports text printing, image printing, paper cutting, and automatic reconnection.

[![React Native](https://img.shields.io/badge/React%20Native-0.70+-blue.svg)](https://reactnative.dev/)
[![Android](https://img.shields.io/badge/Android-5.0+-green.svg)](https://www.android.com/)
[![License](https://img.shields.io/badge/license-MIT-orange.svg)](LICENSE)

## ✨ Features

- ✅ **Auto-detection** of USB thermal printers
- ✅ **Automatic reconnection** when printer is powered on/off or unplugged/replugged
- ✅ **Real-time connection monitoring** with status events
- ✅ **Text printing** with UTF-8 support
- ✅ **Image printing** with automatic monochrome conversion
- ✅ **Paper cutting** command support
- ✅ **ESC/POS** command standard
- ✅ **58mm and 80mm** printer support
- ✅ **Android 5.0 to 14+** compatibility
- ✅ **No additional dependencies** required
- ✅ **Permission handling** built-in

## 📱 Requirements

- React Native >= 0.70
- Android API Level >= 21 (Android 5.0)
- Android device with USB OTG support
- USB OTG cable (if connecting to phone)
- ESC/POS compatible thermal printer

## 🚀 Installation

### Step 1: Create Module Files

Create the following directory structure in your React Native project:

```
android/app/src/main/
├── AndroidManifest.xml
├── java/com/[your-app-name]/
│   ├── MainActivity.kt
│   ├── MainApplication.kt
│   └── printer/
│       ├── UsbPrinterManager.kt
│       ├── UsbPrinterModule.kt
│       └── UsbPrinterPackage.kt
└── res/
    └── xml/
        └── device_filter.xml
```

### Step 2: Add Kotlin Files

Copy the following files to `android/app/src/main/java/com/[your-app-name]/printer/`:

**UsbPrinterManager.kt** - Core printer management logic
**UsbPrinterModule.kt** - React Native bridge
**UsbPrinterPackage.kt** - Package registration

### Step 3: Create Device Filter

Create `android/app/src/main/res/xml/device_filter.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- USB Printer Class -->
    <usb-device class="7" subclass="1" protocol="1" />
    <usb-device class="7" subclass="1" protocol="2" />
    <usb-device class="7" subclass="1" protocol="3" />
</resources>
```

### Step 4: Update AndroidManifest.xml

Add USB permissions and intent filters:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- USB Permissions -->
    <uses-feature android:name="android.hardware.usb.host" android:required="false" />
    <uses-permission android:name="android.permission.USB_PERMISSION" />
    
    <application ...>
      <activity
        android:name=".MainActivity"
        android:exported="true"
        ...>
        
        <!-- Existing intent filter -->
        <intent-filter>
          <action android:name="android.intent.action.MAIN" />
          <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
        
        <!-- USB Device Attached Intent -->
        <intent-filter>
          <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
        </intent-filter>
        
        <!-- USB Device Filter -->
        <meta-data
          android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
          android:resource="@xml/device_filter" />
          
      </activity>
    </application>
</manifest>
```

### Step 5: Register the Package

Update `MainApplication.kt`:

```kotlin
import com.thermalprinterapp.printer.UsbPrinterPackage

class MainApplication : Application(), ReactApplication {
  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList = PackageList(this).packages.apply {
          add(UsbPrinterPackage()) // Add this line
        },
    )
  }
  // ...
}
```

### Step 6: Rebuild the App

```bash
cd android
./gradlew clean
cd ..
npx react-native run-android
```

## 📖 Usage

### Basic Setup

```javascript
import React, { useEffect, useState } from 'react';
import {
  NativeModules,
  NativeEventEmitter,
  Alert
} from 'react-native';

const { UsbPrinter } = NativeModules;
const printerEvents = new NativeEventEmitter(UsbPrinter);

function App() {
  const [printerConnected, setPrinterConnected] = useState(false);

  useEffect(() => {
    // Listen for connection events
    const connectListener = printerEvents.addListener(
      'printerConnected',
      () => {
        setPrinterConnected(true);
        Alert.alert('Printer Connected!');
      }
    );

    const disconnectListener = printerEvents.addListener(
      'printerDisconnected',
      () => {
        setPrinterConnected(false);
        Alert.alert('Printer Disconnected');
      }
    );

    // Cleanup
    return () => {
      connectListener.remove();
      disconnectListener.remove();
    };
  }, []);

  return (
    // Your UI here
  );
}
```

### Print Text

```javascript
const printReceipt = () => {
  if (!printerConnected) {
    Alert.alert('Error', 'Printer not connected!');
    return;
  }

  const receipt = `
================================
       YOUR STORE NAME
================================
Date: ${new Date().toLocaleString()}
Order #: 12345
--------------------------------
Item 1              $10.00
Item 2              $15.00
Item 3               $5.00
--------------------------------
Subtotal:           $30.00
Tax:                 $2.40
--------------------------------
TOTAL:              $32.40
================================
Thank you for your purchase!
`;

  UsbPrinter.printText(receipt);
};
```

### Print Image

```javascript
// Using react-native-image-picker
import { launchImageLibrary } from 'react-native-image-picker';

const printImage = () => {
  launchImageLibrary(
    {
      mediaType: 'photo',
      quality: 1,
      includeBase64: true,
    },
    (response) => {
      if (response.assets && response.assets[0]) {
        const base64 = response.assets[0].base64;
        
        // Print with default width (384 for 58mm)
        UsbPrinter.printImage(base64, 0);
      }
    }
  );
};

// Or print from a URL
const printImageFromUrl = async (imageUrl) => {
  try {
    const response = await fetch(imageUrl);
    const blob = await response.blob();
    
    // Convert blob to base64
    const reader = new FileReader();
    reader.onloadend = () => {
      const base64 = reader.result.split(',')[1];
      UsbPrinter.printImage(base64, 384); // 58mm width
    };
    reader.readAsDataURL(blob);
  } catch (error) {
    console.error('Error fetching image:', error);
  }
};
```

### Cut Paper

```javascript
const cutPaper = () => {
  UsbPrinter.cutPaper();
};
```

### Check Connection Status

```javascript
const checkConnection = () => {
  UsbPrinter.isConnected((connected) => {
    console.log('Printer connected:', connected);
    setPrinterConnected(connected);
  });
};

// Check periodically
useEffect(() => {
  const interval = setInterval(checkConnection, 3000);
  return () => clearInterval(interval);
}, []);
```

## 📚 API Reference

### Methods

#### `printText(text: string): void`

Prints text to the thermal printer. Automatically adds a line feed after the text.

**Parameters:**
- `text` (string): The text to print. Supports UTF-8 characters.

**Example:**
```javascript
UsbPrinter.printText('Hello World!');
```

---

#### `printImage(base64: string, width: number): void`

Prints an image to the thermal printer. Image is automatically converted to monochrome.

**Parameters:**
- `base64` (string): Base64-encoded image data (without data URI prefix)
- `width` (number): Target width in pixels. Use `0` for default printer width, `384` for 58mm, `576` for 80mm

**Example:**
```javascript
UsbPrinter.printImage(base64ImageData, 384);
```

**Supported formats:** JPG, PNG, BMP, GIF

---

#### `cutPaper(): void`

Sends a paper cut command to the printer.

**Example:**
```javascript
UsbPrinter.cutPaper();
```

---

#### `setPrinterWidth(width: number): void`

Sets the printer width for image printing.

**Parameters:**
- `width` (number): `384` for 58mm printers, `576` for 80mm printers

**Example:**
```javascript
UsbPrinter.setPrinterWidth(384); // 58mm
UsbPrinter.setPrinterWidth(576); // 80mm
```

---

#### `isConnected(callback: (connected: boolean) => void): void`

Checks if a printer is currently connected.

**Parameters:**
- `callback` (function): Callback function that receives connection status

**Example:**
```javascript
UsbPrinter.isConnected((connected) => {
  if (connected) {
    console.log('Printer is connected');
  } else {
    console.log('Printer is disconnected');
  }
});
```

### Events

#### `printerConnected`

Emitted when a printer is successfully connected.

**Example:**
```javascript
printerEvents.addListener('printerConnected', () => {
  console.log('Printer connected!');
});
```

---

#### `printerDisconnected`

Emitted when the printer is disconnected (unplugged, powered off, or connection lost).

**Example:**
```javascript
printerEvents.addListener('printerDisconnected', () => {
  console.log('Printer disconnected!');
});
```

## 🖨️ Printer Width Configuration

The module supports both 58mm and 80mm thermal printers:

| Printer Size | Width (pixels) | Printable Width |
|--------------|----------------|-----------------|
| 58mm | 384 | ~48mm |
| 80mm | 576 | ~72mm |

```javascript
// For 58mm printers
UsbPrinter.setPrinterWidth(384);

// For 80mm printers
UsbPrinter.setPrinterWidth(576);
```

## 🖼️ Image Printing

### Image Processing

Images are automatically processed before printing:
1. **Resized** to fit printer width (maintaining aspect ratio)
2. **Converted** to monochrome (black & white)
3. **Encoded** in ESC/POS bitmap format
4. **Centered** on the paper

### Best Practices

✅ **DO:**
- Use high-contrast images
- Prefer simple, clear graphics
- Use appropriate resolution (300-600 DPI)
- Test with small images first

❌ **DON'T:**
- Use low-contrast or faded images
- Print extremely large images (>1000px height)
- Use images with fine details

### Example: Print Logo

```javascript
const printLogo = async () => {
  // Logo as base64 (or load from assets)
  const logoBase64 = 'iVBORw0KGgoAAAANSUhEUgAA...';
  
  UsbPrinter.printText('\n'); // Add spacing
  UsbPrinter.printImage(logoBase64, 200); // 200px width
  UsbPrinter.printText('\n'); // Add spacing
  UsbPrinter.printText('Welcome to Our Store!\n');
};
```

## 🔄 Connection Management

### Auto-Reconnection

The module automatically handles reconnection in the following scenarios:

1. **Printer powered off/on** → Reconnects within 3-5 seconds
2. **USB cable unplugged/replugged** → Reconnects immediately
3. **App opened with printer off** → Connects when printer powers on

### Connection Monitoring

- **Health checks every 2 seconds** when connected
- **Reconnection scans every 3 seconds** when disconnected
- **Automatic disconnect detection** on failed print operations

### Manual Reconnection

No manual reconnection is needed! The module handles everything automatically.

### Connection Status

```javascript
const [status, setStatus] = useState('Checking...');

useEffect(() => {
  const connectListener = printerEvents.addListener(
    'printerConnected',
    () => setStatus('Connected ✅')
  );

  const disconnectListener = printerEvents.addListener(
    'printerDisconnected',
    () => setStatus('Disconnected ❌')
  );

  // Verify status periodically
  const interval = setInterval(() => {
    UsbPrinter.isConnected((connected) => {
      setStatus(connected ? 'Connected ✅' : 'Disconnected ❌');
    });
  }, 3000);

  return () => {
    connectListener.remove();
    disconnectListener.remove();
    clearInterval(interval);
  };
}, []);
```

## 🐛 Troubleshooting

### Printer Not Detected

**Problem:** Printer is connected but app shows "Disconnected"

**Solutions:**
1. Check USB OTG cable is working
2. Grant USB permission when prompted
3. Enable OTG in device settings (some phones)
4. Try a different USB port
5. Restart the printer

```bash
# Check if device is detected
adb shell lsusb
```

---

### Permission Dialog Not Appearing

**Problem:** App doesn't ask for USB permission

**Solutions:**
1. Unplug and replug the printer
2. Check `AndroidManifest.xml` has correct intent filters
3. Check `device_filter.xml` exists
4. Restart the app

```bash
# Check USB permissions
adb shell dumpsys package com.yourapp | grep permission
```

---

### Print Command Sent but Nothing Prints

**Problem:** Status shows connected, but printer doesn't print

**Solutions:**
1. Check printer has paper loaded
2. Check printer is not in error state (paper jam, cover open)
3. Check printer power supply
4. Try the test print button on printer (if available)
5. Check printer compatibility (must support ESC/POS)

---

### Image Doesn't Print or Prints Incorrectly

**Problem:** Image printing fails or prints garbage

**Solutions:**
1. Ensure image is proper base64 format
2. Remove data URI prefix (`data:image/png;base64,`)
3. Try smaller image size
4. Increase image contrast
5. Check printer supports graphics

```javascript
// Correct: Remove prefix
let base64 = imageData.split(',')[1];
UsbPrinter.printImage(base64, 384);

// Incorrect: With prefix
UsbPrinter.printImage('data:image/png;base64,iVBORw...', 384); // ❌
```

---

### App Crashes on Connection

**Problem:** App crashes when printer connects

**Solutions:**
1. Clean and rebuild: `cd android && ./gradlew clean`
2. Check all Kotlin files are updated
3. Check Android version compatibility
4. Check logcat for errors

```bash
# View crash logs
adb logcat | grep AndroidRuntime
```

---

### Reconnection Takes Too Long

**Problem:** Printer takes >10 seconds to reconnect

**Possible Causes:**
- Printer slow to boot (some printers take 5-10 seconds)
- USB mode changed on device
- Permission dialog waiting for user input

---

### Debugging

Enable detailed logging:

```bash
# View all printer logs
adb logcat | grep UsbPrinterManager

# Key messages to look for:
# - "Printer connected successfully"
# - "Printer disconnected"
# - "Reconnection monitoring started"
# - "Checking for printer reconnection..."
```

## 📱 Example App

A complete example app is included. Here's the basic structure:

```javascript
import React, { useEffect, useState } from 'react';
import {
  SafeAreaView,
  Text,
  TextInput,
  TouchableOpacity,
  NativeModules,
  NativeEventEmitter,
  StyleSheet,
  Alert,
} from 'react-native';

const { UsbPrinter } = NativeModules;
const printerEvents = new NativeEventEmitter(UsbPrinter);

export default function App() {
  const [connected, setConnected] = useState(false);
  const [text, setText] = useState('');

  useEffect(() => {
    const connectListener = printerEvents.addListener(
      'printerConnected',
      () => setConnected(true)
    );

    const disconnectListener = printerEvents.addListener(
      'printerDisconnected',
      () => setConnected(false)
    );

    return () => {
      connectListener.remove();
      disconnectListener.remove();
    };
  }, []);

  const handlePrint = () => {
    if (!connected) {
      Alert.alert('Error', 'Printer not connected!');
      return;
    }
    UsbPrinter.printText(text);
    Alert.alert('Success', 'Print command sent!');
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.status}>
        Status: {connected ? '✅ Connected' : '❌ Disconnected'}
      </Text>

      <TextInput
        style={styles.input}
        placeholder="Enter text to print"
        value={text}
        onChangeText={setText}
        multiline
      />

      <TouchableOpacity
        style={[styles.button, !connected && styles.disabled]}
        onPress={handlePrint}
        disabled={!connected}
      >
        <Text style={styles.buttonText}>Print</Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.button, !connected && styles.disabled]}
        onPress={() => UsbPrinter.cutPaper()}
        disabled={!connected}
      >
        <Text style={styles.buttonText}>Cut Paper</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
  },
  status: {
    fontSize: 18,
    marginBottom: 20,
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    padding: 10,
    marginBottom: 20,
    minHeight: 100,
  },
  button: {
    backgroundColor: '#007bff',
    padding: 15,
    borderRadius: 8,
    marginBottom: 10,
    alignItems: 'center',
  },
  disabled: {
    backgroundColor: '#ccc',
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
```

## 🔧 Advanced Usage

### Custom ESC/POS Commands

You can send custom ESC/POS commands by extending the module:

```kotlin
// In UsbPrinterManager.kt
fun sendCustomCommand(command: ByteArray) {
    sendToPrinter(command)
}
```

### Common ESC/POS Commands

```javascript
// Bold text
const BOLD_ON = [0x1B, 0x45, 0x01];
const BOLD_OFF = [0x1B, 0x45, 0x00];

// Text alignment
const ALIGN_LEFT = [0x1B, 0x61, 0x00];
const ALIGN_CENTER = [0x1B, 0x61, 0x01];
const ALIGN_RIGHT = [0x1B, 0x61, 0x02];

// Font size
const FONT_NORMAL = [0x1D, 0x21, 0x00];
const FONT_DOUBLE_HEIGHT = [0x1D, 0x21, 0x01];
const FONT_DOUBLE_WIDTH = [0x1D, 0x21, 0x10];
const FONT_DOUBLE_BOTH = [0x1D, 0x21, 0x11];
```

### Formatting Helper

```javascript
const formatReceipt = (items) => {
  let receipt = '\n';
  receipt += '================================\n';
  receipt += '         RECEIPT\n';
  receipt += '================================\n';
  receipt += `Date: ${new Date().toLocaleString()}\n`;
  receipt += '--------------------------------\n';
  
  items.forEach(item => {
    const price = item.price.toFixed(2).padStart(8);
    const name = item.name.padEnd(22);
    receipt += `${name}${price}\n`;
  });
  
  receipt += '--------------------------------\n';
  const total = items.reduce((sum, item) => sum + item.price, 0);
  receipt += `TOTAL:${total.toFixed(2).padStart(24)}\n`;
  receipt += '================================\n';
  receipt += 'Thank you!\n\n\n';
  
  return receipt;
};
```

## 📊 Supported Printers

This module has been tested with:

- ✅ Most ESC/POS compatible thermal printers
- ✅ 58mm receipt printers
- ✅ 80mm receipt printers
- ✅ USB interface printers

**Popular brands:**
- Epson TM series
- Star Micronics
- Bixolon
- Citizen
- Generic Chinese thermal printers

## 🔐 Permissions

The module requires the following Android permissions:

```xml
<uses-feature android:name="android.hardware.usb.host" />
<uses-permission android:name="android.permission.USB_PERMISSION" />
```

These are automatically requested when needed.

## 📝 License

MIT License

Copyright (c) 2025

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

## Commercial Use Permission
This software is provided under the MIT License, which explicitly permits:

✅ Commercial use in proprietary software

✅ Distribution in commercial products

✅ Modification and customization

✅ Private use

✅ Patent use

✅ Sublicensing

✅ Inclusion in commercial applications

### No Warranty
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📞 Support

If you encounter any issues:

1. Check the [Troubleshooting](#troubleshooting) section
2. Enable debug logging: `adb logcat | grep UsbPrinterManager`
3. Open an issue with:
   - Device model and Android version
   - Printer model
   - Error logs
   - Steps to reproduce

## 🗺️ Roadmap

- [ ] Bluetooth printer support
- [ ] Network (WiFi) printer support
- [ ] QR code printing
- [ ] Barcode printing
- [ ] Custom font support
- [ ] iOS support

## ⭐ Acknowledgments

- ESC/POS command reference
- React Native community
- Android USB Host API documentation

---

Made with ❤️ for the React Native community