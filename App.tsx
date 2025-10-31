import React, { useEffect, useState } from "react";
import {
  SafeAreaView,
  Text,
  TextInput,
  TouchableOpacity,
  NativeModules,
  NativeEventEmitter,
  StyleSheet,
  ScrollView,
  Alert,
  Platform,
  Image,
  View,
} from "react-native";
import { launchImageLibrary } from "react-native-image-picker";

const { UsbPrinter } = NativeModules;

if (!UsbPrinter) {
  console.error("UsbPrinter module is not available!");
}

const printerEvents = UsbPrinter ? new NativeEventEmitter(UsbPrinter) : null;

export default function App() {
  const [printerConnected, setPrinterConnected] = useState(false);
  const [text, setText] = useState("");
  const [status, setStatus] = useState("Checking...");
  const [selectedImage, setSelectedImage] = useState(null);
  const [printerWidth, setPrinterWidthState] = useState(384); // 58mm default

  useEffect(() => {
    if (!printerEvents) {
      setStatus("Module not loaded");
      return;
    }

    setStatus("Waiting for printer...");

    const connectListener = printerEvents.addListener(
      "printerConnected",
      () => {
        console.log("Printer connected event received");
        setPrinterConnected(true);
        setStatus("Connected ✅");
      }
    );

    const disconnectListener = printerEvents.addListener(
      "printerDisconnected",
      () => {
        console.log("Printer disconnected event received");
        setPrinterConnected(false);
        setStatus("Disconnected ❌");
      }
    );
    
    // Verify actual connection status every 3 seconds
    const statusCheckInterval = setInterval(() => {
      if (UsbPrinter && UsbPrinter.isConnected) {
        UsbPrinter.isConnected((connected) => {
          if (connected !== printerConnected) {
            console.log(`Status mismatch detected. Updating to: ${connected}`);
            setPrinterConnected(connected);
            setStatus(connected ? "Connected ✅" : "Disconnected ❌");
          }
        });
      }
    }, 3000);

    return () => {
      connectListener.remove();
      disconnectListener.remove();
      clearInterval(statusCheckInterval);
    };
  }, [printerConnected]);

  const handlePrintText = () => {
    if (!printerConnected) {
      Alert.alert("Error", "Printer is not connected!");
      return;
    }

    if (!text.trim()) {
      Alert.alert("Error", "Please enter text to print!");
      return;
    }

    try {
      UsbPrinter.printText(text);
      Alert.alert("Success", "Print command sent!");
    } catch (error) {
      Alert.alert("Error", `Failed to print: ${error.message}`);
      console.error("Print error:", error);
    }
  };

  const handleSelectImage = () => {
    // Alert.alert(
    //   "Image Picker",
    //   "not found!"
    // );
    
    
    const options = {
      mediaType: "photo",
      quality: 1,
      includeBase64: true,
    };

    launchImageLibrary(options, (response) => {
      if (response.didCancel) {
        console.log("User cancelled image picker");
      } else if (response.errorCode) {
        Alert.alert("Error", response.errorMessage);
      } else if (response.assets && response.assets[0]) {
        const asset = response.assets[0];
        setSelectedImage({
          uri: asset.uri,
          base64: asset.base64,
        });
      }
    });
    
  };

  const handlePrintImage = () => {
    if (!printerConnected) {
      Alert.alert("Error", "Printer is not connected!");
      return;
    }

    if (!selectedImage) {
      Alert.alert("Error", "Please select an image first!");
      return;
    }

    try {
      // Remove data:image prefix if present
      let base64Data = selectedImage.base64;
      if (base64Data.includes(",")) {
        base64Data = base64Data.split(",")[1];
      }

      UsbPrinter.printImage(base64Data, printerWidth);
      Alert.alert("Success", "Image print command sent!");
    } catch (error) {
      Alert.alert("Error", `Failed to print image: ${error.message}`);
      console.error("Print image error:", error);
    }
  };

  const handleCutPaper = () => {
    if (!printerConnected) {
      Alert.alert("Error", "Printer is not connected!");
      return;
    }

    try {
      UsbPrinter.cutPaper();
      Alert.alert("Success", "Cut paper command sent!");
    } catch (error) {
      Alert.alert("Error", `Failed to cut paper: ${error.message}`);
      console.error("Cut paper error:", error);
    }
  };

  const handleTestPrint = () => {
    if (!printerConnected) {
      Alert.alert("Error", "Printer is not connected!");
      return;
    }

    try {
      const testText = `
================================
       TEST PRINT
================================
Date: ${new Date().toLocaleString()}
Status: OK
Printer Width: ${printerWidth === 384 ? "58mm" : "80mm"}
--------------------------------
This is a test print from the
USB Thermal Printer App
================================
`;
      UsbPrinter.printText(testText);
      Alert.alert("Success", "Test print sent!");
    } catch (error) {
      Alert.alert("Error", `Failed to test print: ${error.message}`);
      console.error("Test print error:", error);
    }
  };

  const changePrinterWidth = (width) => {
    setPrinterWidthState(width);
    UsbPrinter.setPrinterWidth(width);
    Alert.alert(
      "Printer Width Changed",
      `Set to ${width === 384 ? "58mm" : "80mm"}`
    );
  };

  if (!UsbPrinter) {
    return (
      <SafeAreaView style={styles.container}>
        <Text style={styles.errorText}>
          UsbPrinter module not found. Please rebuild the app.
        </Text>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title}>USB Thermal Printer</Text>

        {/* Status Card */}
        <View style={styles.statusCard}>
          <Text style={styles.statusLabel}>Printer Status:</Text>
          <Text
            style={[
              styles.statusText,
              { color: printerConnected ? "#28a745" : "#dc3545" },
            ]}
          >
            {status}
          </Text>
        </View>

        {/* Printer Width Selector */}
        <View style={styles.widthSelector}>
          <Text style={styles.label}>Printer Width:</Text>
          <View style={styles.widthButtons}>
            <TouchableOpacity
              onPress={() => changePrinterWidth(384)}
              style={[
                styles.widthButton,
                printerWidth === 384 && styles.widthButtonActive,
              ]}
            >
              <Text
                style={[
                  styles.widthButtonText,
                  printerWidth === 384 && styles.widthButtonTextActive,
                ]}
              >
                58mm
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={() => changePrinterWidth(576)}
              style={[
                styles.widthButton,
                printerWidth === 576 && styles.widthButtonActive,
              ]}
            >
              <Text
                style={[
                  styles.widthButtonText,
                  printerWidth === 576 && styles.widthButtonTextActive,
                ]}
              >
                80mm
              </Text>
            </TouchableOpacity>
          </View>
        </View>

      
        {/* Text Input */}
        <Text style={styles.label}>Text to Print:</Text>
        <TextInput
          placeholder="Enter text here..."
          value={text}
          onChangeText={setText}
          multiline
          numberOfLines={4}
          style={styles.input}
          editable={printerConnected}
        />

        {/* Text Print Button */}
        <TouchableOpacity
          onPress={handlePrintText}
          style={[
            styles.button,
            styles.primaryButton,
            !printerConnected && styles.disabledButton,
          ]}
          disabled={!printerConnected}
        >
          <Text style={styles.buttonText}>🖨️ Print Text</Text>
        </TouchableOpacity>

        {/* Image Section */}
        <View style={styles.section}>
          <Text style={styles.label}>Print Image:{'\n'}The image ,for now is in maintenance mode !!!</Text>
          
          {selectedImage && (
            <View style={styles.imagePreview}>
              <Image
                source={{ uri: selectedImage.uri }}
                style={styles.previewImage}
                resizeMode="contain"
              />
            </View>
          )}

          <TouchableOpacity
            onPress={handleSelectImage}
            style={[
              styles.button,
              styles.secondaryButton,
              !printerConnected && styles.disabledButton,
            ]}
            disabled={!printerConnected}
          >
            <Text style={styles.buttonText}>
              📷 {selectedImage ? "Change Image" : "Select Image"}
            </Text>
          </TouchableOpacity>

          {selectedImage && (
            <TouchableOpacity
              onPress={handlePrintImage}
              style={[styles.button, styles.primaryButton]}
            >
              <Text style={styles.buttonText}>🖼️ Print Image</Text>
            </TouchableOpacity>
          )}
        </View>

        {/* Other Buttons */}
        <TouchableOpacity
          onPress={handleTestPrint}
          style={[
            styles.button,
            styles.infoButton,
            !printerConnected && styles.disabledButton,
          ]}
          disabled={!printerConnected}
        >
          <Text style={styles.buttonText}>📄 Test Print</Text>
        </TouchableOpacity>

        <TouchableOpacity
          onPress={handleCutPaper}
          style={[
            styles.button,
            styles.successButton,
            !printerConnected && styles.disabledButton,
          ]}
          disabled={!printerConnected}
        >
          <Text style={styles.buttonText}>✂️ Cut Paper</Text>
        </TouchableOpacity>

        {/* Footer */}
        <Text style={styles.footer}>
          Platform: {Platform.OS} | Version: {Platform.Version}
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f5f5f5",
  },
  scrollContent: {
    padding: 20,
    paddingBottom: 40,
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    textAlign: "center",
    marginBottom: 20,
    color: "#333",
  },
  statusCard: {
    backgroundColor: "#fff",
    padding: 15,
    borderRadius: 10,
    marginBottom: 20,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  statusLabel: {
    fontSize: 14,
    color: "#666",
    marginBottom: 5,
  },
  statusText: {
    fontSize: 18,
    fontWeight: "bold",
  },
  widthSelector: {
    backgroundColor: "#fff",
    padding: 15,
    borderRadius: 10,
    marginBottom: 20,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  widthButtons: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: 10,
  },
  widthButton: {
    flex: 1,
    padding: 12,
    marginHorizontal: 5,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: "#007bff",
    backgroundColor: "#fff",
    alignItems: "center",
  },
  widthButtonActive: {
    backgroundColor: "#007bff",
  },
  widthButtonText: {
    fontSize: 16,
    fontWeight: "600",
    color: "#007bff",
  },
  widthButtonTextActive: {
    color: "#fff",
  },
  label: {
    fontSize: 16,
    fontWeight: "600",
    marginBottom: 8,
    color: "#333",
  },
  input: {
    borderWidth: 1,
    borderColor: "#ddd",
    borderRadius: 8,
    padding: 12,
    marginBottom: 20,
    backgroundColor: "#fff",
    fontSize: 16,
    textAlignVertical: "top",
    minHeight: 100,
  },
  section: {
    marginBottom: 20,
  },
  imagePreview: {
    width: "100%",
    height: 200,
    backgroundColor: "#f0f0f0",
    borderRadius: 10,
    marginBottom: 15,
    overflow: "hidden",
  },
  previewImage: {
    width: "100%",
    height: "100%",
  },
  button: {
    padding: 15,
    borderRadius: 8,
    marginBottom: 12,
    alignItems: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 3,
    elevation: 3,
  },
  primaryButton: {
    backgroundColor: "#007bff",
  },
  secondaryButton: {
    backgroundColor: "#6c757d",
  },
  infoButton: {
    backgroundColor: "#17a2b8",
  },
  successButton: {
    backgroundColor: "#28a745",
  },
  disabledButton: {
    backgroundColor: "#ccc",
    opacity: 0.6,
  },
  buttonText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "600",
  },
  footer: {
    textAlign: "center",
    marginTop: 20,
    fontSize: 12,
    color: "#999",
  },
  errorText: {
    color: "#dc3545",
    fontSize: 16,
    textAlign: "center",
    padding: 20,
  },
});