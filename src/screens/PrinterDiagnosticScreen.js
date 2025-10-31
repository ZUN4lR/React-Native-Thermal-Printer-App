import React, { useEffect, useState } from 'react';
import { View, Text, FlatList, TouchableOpacity, StyleSheet } from 'react-native';
import { USBPrinter } from 'react-native-usb-thermal-printer';
import { connectToPrinter } from '../utils/printerCommands';

const PrinterDiagnosticScreen = ({ navigation }) => {
  const [printers, setPrinters] = useState([]);
  const [selectedPrinter, setSelectedPrinter] = useState(null);

  useEffect(() => {
    scanPrinters();
  }, []);

  const scanPrinters = async () => {
    try {
      const devices = await USBPrinter.getDeviceList();
      setPrinters(devices);
    } catch (error) {
      console.error('USB scan failed:', error);
    }
  };

  const handleSelectPrinter = async (printer) => {
    const connected = await connectToPrinter(USBPrinter, printer);
    if (connected) setSelectedPrinter(printer);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>USB Printers Detected</Text>

      <FlatList
        data={printers}
        keyExtractor={(item) => item.deviceId.toString()}
        renderItem={({ item }) => (
          <TouchableOpacity style={styles.item} onPress={() => handleSelectPrinter(item)}>
            <Text style={styles.itemText}>{item.deviceName || 'Unknown Printer'}</Text>
            <Text style={styles.subText}>Vendor: {item.vendorId} | Product: {item.productId}</Text>
          </TouchableOpacity>
        )}
        ListEmptyComponent={<Text>No Printers Found</Text>}
      />

      {selectedPrinter && (
        <TouchableOpacity
          style={styles.nextButton}
          onPress={() => navigation.navigate('PrintText')}
        >
          <Text style={styles.nextButtonText}>Printer Selected! Go to Print Screen</Text>
        </TouchableOpacity>
      )}
    </View>
  );
};

export default PrinterDiagnosticScreen;

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20 },
  title: { fontSize: 22, fontWeight: 'bold', marginBottom: 20 },
  item: { padding: 15, backgroundColor: '#eee', marginBottom: 10, borderRadius: 10 },
  itemText: { fontSize: 18, fontWeight: 'bold' },
  subText: { fontSize: 12 },
  nextButton: { marginTop: 20, padding: 15, backgroundColor: '#000', borderRadius: 8 },
  nextButtonText: { color: '#fff', textAlign: 'center', fontSize: 16 },
});
