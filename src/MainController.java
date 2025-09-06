import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainController {
    public static class PaperSize {
        private final String name;
        private final String dimensions;

        public PaperSize(String name, String dimensions) {
            this.name = name;
            this.dimensions = dimensions;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name + " (" + dimensions + ")";
        }
    }
    
    @FXML private Button btnStart;
    @FXML private Button btnPause;
    @FXML private ComboBox<String> printerCombo;
    @FXML private ComboBox<PaperSize> paperCombo;
    @FXML private Button btnPauseLoading; // Button loading baru
    @FXML private ComboBox<String> marketplaceCombo;

    private volatile boolean isPaused = false;
    private Thread processingThread = null;

    @FXML
    @SuppressWarnings("unchecked")
    private void handleStart() {
        btnStart.setVisible(false);
        btnPause.setVisible(true);
        printerCombo.setDisable(true);
        paperCombo.setDisable(true);
        marketplaceCombo.setDisable(true);

        String selectedPrinter = printerCombo.getValue() != null ? printerCombo.getValue() : "";
        String selectedPaper = paperCombo.getValue() != null ? paperCombo.getValue().getName() : "";
        String selectedMarketplace = marketplaceCombo.getValue() != null ? marketplaceCombo.getValue() : "";
        String selectedMarketplaceLowercase = selectedMarketplace != null ? selectedMarketplace.toLowerCase() : "";

        Integer limit = 1;

        MediaSizeName tempPaperSize = null;
        if ("A4".equals(selectedPaper)) tempPaperSize = MediaSizeName.ISO_A4;
        else if ("A5".equals(selectedPaper)) tempPaperSize = MediaSizeName.ISO_A5;
        else if ("Letter".equals(selectedPaper)) tempPaperSize = MediaSizeName.NA_LETTER;

        final MediaSizeName paperSize = tempPaperSize;
        System.out.println("paper size" + paperSize);
        isPaused = false;

        processingThread = new Thread(() -> {
            try {
                // Main processing loop
                while (!isPaused) {
                    System.out.println("=== Starting new cycle ===");
                    
                    // Get current date
                    LocalDateTime now = LocalDateTime.now().minusHours(7);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String formattedDate = now.format(formatter);

                    List<String> numbersToSendUpdate = new ArrayList<>();
                    boolean isMore = false;

                    // Step 1: Initial data fetch to check is_more
                    System.out.println("Step 1: Checking is_more status...");
                    System.out.println("Selected Marketplace: " + selectedMarketplaceLowercase);
                    System.out.println("Limit: " + limit);
                    System.out.println("Selected Date: " + formattedDate);
                    
                    DOResponse resp = DOFetcher.fetchDO("https://aloy.id/warehouse/get/do-resi", 
                        String.format("{\"marketplace\":\"%s\",\"limit\":%d,\"start_date\":\"%s\"}", 
                            selectedMarketplaceLowercase.replace("\"", "\\\""), limit.intValue(), formattedDate.replace("\"", "\\\"")));

                    System.out.println("Status   : " + resp.result.status);
                    System.out.println("Message  : " + resp.result.msg);
                    
                    isMore = resp.result.is_more;
                    System.out.println("Is More : " + isMore);

                    // Step 2: Main logic loop - runs forever until paused
                    while (!isPaused) {
                        if (isMore) {
                            System.out.println("Step 2a: is_more = true, fetching picking data...");
                            
                            // Fetch data for picking
                            DOResponse pickingResp = DOFetcher.fetchDO("https://aloy.id/warehouse/get/do-resi", 
                                String.format("{\"marketplace\":\"%s\",\"limit\":%d,\"start_date\":\"%s\"}", 
                                    selectedMarketplaceLowercase.replace("\"", "\\\""), limit.intValue(), formattedDate.replace("\"", "\\\"")));

                            // Map<String, String> pickingItems = getItemsAsMap(pickingResp);
                            Map<String, String> pickingItems = new HashMap<>();
                            for (Map<String, String> item : pickingResp.result.items) {
                                pickingItems.putAll(item);
                            }
                            System.out.println("Picking Data: " + (pickingItems != null && !pickingItems.isEmpty() ? pickingItems.keySet() : "empty"));

                            // Process picking data if available
                            if (!isPaused && pickingItems != null && !pickingItems.isEmpty()) {
                                System.out.println("Step 3: Processing picking data, count: " + pickingItems.size());
                                
                                // Clear previous numbers and extract from current data
                                numbersToSendUpdate.clear();
                                numbersToSendUpdate.addAll(pickingItems.keySet());
                                System.out.println("Numbers to update: " + numbersToSendUpdate);

                                // Print all picking data
                                System.out.println("Step 4: Printing all picking data...");
                                for (Map.Entry<String, String> entry : pickingItems.entrySet()) {
                                    if (isPaused) break;
                                    
                                    String number = entry.getKey();
                                    String pdfBinary = entry.getValue();
                                    
                                    System.out.println("Printing document for number: " + number);
                                    try {
                                        byte[] pdfData = java.util.Base64.getDecoder().decode(pdfBinary);
                                        
                                        // Method 1: Try with PrintJobListener (timeout 15s)  
                                        PDFPrinterWithStatus.PrintResult result = 
                                            PDFPrinterWithStatus.printAndWaitWithStatus(pdfData, selectedPrinter, 15000);
                                        
                                        if (result.isSuccess()) {
                                            System.out.println("Print berhasil untuk " + number + " (Method 1)");
                                        } else {
                                            System.out.println("Method 1 failed untuk " + number + ": " + result.getMessage());
                                            
                                            // Method 2: Try polling method
                                            System.out.println("Trying polling method...");
                                            PDFPrinterWithStatus.PrintResult altResult = 
                                                PDFPrinterWithStatus.printAndWaitWithPolling(pdfData, selectedPrinter, 15000);
                                            
                                            if (altResult.isSuccess()) {
                                                System.out.println("Polling method berhasil untuk " + number);
                                            } else {
                                                System.out.println("Both methods failed untuk " + number);
                                                System.out.println("  - Method 1: " + result.getMessage());
                                                System.out.println("  - Method 2: " + altResult.getMessage());
                                            }
                                        }
                                        
                                    } catch (Exception e) {
                                        System.err.println("❌ Error printing document " + number + ": " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                    
                                    // Simulate printing time per document
                                    if (!isPaused) Thread.sleep(500);
                                }
                                System.out.println("Printing completed for batch of " + pickingItems.size() + " items");

                                // Step 5: Update API to mark as processed
                                if (!isPaused) {
                                    System.out.println("Step 5: Updating API to mark items as processed...");
                                    updateProcessedItems(numbersToSendUpdate, selectedMarketplaceLowercase);
                                }
                            } else {
                                System.out.println("No picking data to process, will retry...");
                            }

                            // Update is_more status for next iteration
                            isMore = pickingResp.result.is_more;
                            
                        } else {
                            System.out.println("Step 2b: is_more = false, updating date and fetching data...");
                            
                            // Update date
                            LocalDateTime newNow = LocalDateTime.now().minusHours(7);
                            String newFormattedDate = newNow.format(formatter);
                            System.out.println("New date: " + newFormattedDate);
                            
                            // Fetch data for picking with new date
                            DOResponse newPickingResp = DOFetcher.fetchDO("https://aloy.id/warehouse/get/do-resi", 
                                String.format("{\"marketplace\":\"%s\",\"limit\":%d,\"start_date\":\"%s\"}", 
                                    selectedMarketplaceLowercase.replace("\"", "\\\""), limit.intValue(), newFormattedDate.replace("\"", "\\\"")));

                            // Use helper method to safely get items as Map
                            // Map<String, String> newPickingItems = getItemsAsMap(newPickingResp);
                            Map<String, String> newPickingItems = new HashMap<>();
                            for (Map<String, String> item : newPickingResp.result.items) {
                                newPickingItems.putAll(item);
                            }
                            System.out.println("New Picking Data: " + (newPickingItems != null && !newPickingItems.isEmpty() ? newPickingItems.keySet() : "empty"));

                            // Process picking data if available
                            if (!isPaused && newPickingItems != null && !newPickingItems.isEmpty()) {
                                System.out.println("Step 3 (new date): Processing picking data, count: " + newPickingItems.size());
                                
                                // Clear previous numbers and extract from current data
                                numbersToSendUpdate.clear();
                                numbersToSendUpdate.addAll(newPickingItems.keySet());
                                System.out.println("Numbers to update: " + numbersToSendUpdate);

                                // Print all picking data
                                System.out.println("Step 4 (new date): Printing all picking data...");
                                for (Map.Entry<String, String> entry : newPickingItems.entrySet()) {
                                    if (isPaused) break;
                                    
                                    String number = entry.getKey();
                                    String pdfBinary = entry.getValue();
                                    
                                    System.out.println("Printing document for number: " + number);
                                    try {
                                        byte[] pdfData = java.util.Base64.getDecoder().decode(pdfBinary);
                                        
                                        // ✅ FIXED: Use pdfData instead of pdfBytes
                                        PDFPrinterWithStatus.PrintResult result = 
                                            PDFPrinterWithStatus.printAndWaitWithStatus(pdfData, selectedPrinter);
                                        
                                        if (result.isSuccess()) {
                                            System.out.println("✅ Print berhasil untuk " + number);
                                        } else {
                                            System.out.println("❌ Print gagal untuk " + number + ": " + result.getMessage());
                                            
                                            // Try alternative method if first failed
                                            System.out.println("Trying alternative printing method...");
                                            PDFPrinterWithStatus.PrintResult altResult = 
                                                PDFPrinterWithStatus.printAndWaitWithPolling(pdfData, selectedPrinter, 30000);
                                            
                                            if (altResult.isSuccess()) {
                                                System.out.println("✅ Alternative print berhasil untuk " + number);
                                            } else {
                                                System.out.println("❌ Both methods failed untuk " + number + ": " + altResult.getMessage());
                                            }
                                        }
                                        
                                    } catch (Exception e) {
                                        System.err.println("❌ Error printing document " + number + ": " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                    
                                    // Simulate printing time per document
                                    if (!isPaused) Thread.sleep(500);
                                }
                                System.out.println("Printing completed for batch of " + newPickingItems.size() + " items");

                                // Step 5: Update API to mark as processed
                                if (!isPaused) {
                                    System.out.println("Step 5 (new date): Updating API to mark items as processed...");
                                    updateProcessedItems(numbersToSendUpdate, selectedMarketplaceLowercase);
                                }
                            } else {
                                System.out.println("No picking data to process, will retry...");
                            }

                            // Update is_more status and formattedDate for next iteration
                            isMore = newPickingResp.result.is_more;
                            formattedDate = newFormattedDate;
                        }
                        
                        // Delay before next iteration (whether data found or not)
                        if (!isPaused) {
                            System.out.println("Waiting before next check...");
                            Thread.sleep(3000); // 3 second delay between checks
                        }
                    }
                    
                    System.out.println("=== Cycle paused by user ===");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                System.out.println("Processing thread ended");
            }
        });
        
        processingThread.start();
    }

    @FXML
    private void handlePause() {
        // 1. Langsung set flag pause
        isPaused = true;
        
        // 2. Show loading state immediately
        btnPause.setVisible(false);
        btnPauseLoading.setVisible(true);
        btnPauseLoading.setDisable(true);
        
        // 3. Disable combo boxes immediately
        printerCombo.setDisable(true);
        paperCombo.setDisable(true);
        marketplaceCombo.setDisable(true);
        
        System.out.println("Pausing process...");
        
        // 4. Do the waiting in background thread to keep UI responsive
        new Thread(() -> {
            try {
                // Wait for processing thread to finish
                if (processingThread != null && processingThread.isAlive()) {
                    processingThread.join(5000); // Wait max 5 seconds
                }
            } catch (InterruptedException e) {
                if (processingThread != null) {
                    processingThread.interrupt();
                }
            } finally {
                // 5. Update UI back to start state (must run on JavaFX thread)
                javafx.application.Platform.runLater(() -> {
                    btnPauseLoading.setVisible(false);
                    btnStart.setVisible(true);
                    printerCombo.setDisable(false);
                    paperCombo.setDisable(false);
                    marketplaceCombo.setDisable(false);
                    
                    System.out.println("Process paused successfully");
                });
            }
        }).start();
    }

    /**
     * Helper method to safely cast items to Map with proper error handling
     * Handles both scenarios: items as Map (when has data) or ArrayList (when empty)
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> getItemsAsMap(DOResponse response) {
        if (response == null || response.result == null || response.result.items == null) {
            System.out.println("Response or items is null, returning empty map");
            return new HashMap<>();
        }
        
        try {
            // Check if items is a Map (when has data)
            if (response.result.items instanceof Map) {
                System.out.println("Items is a Map, casting...");
                return (Map<String, String>) response.result.items;
            }
            // Check if items is a List (when empty)
            else if (response.result.items instanceof List) {
                List<?> itemsList = (List<?>) response.result.items;
                System.out.println("Items is empty List, size: " + itemsList.size());
                return new HashMap<>(); // Return empty map
            }
            else {
                System.err.println("Unexpected items type: " + response.result.items.getClass().getName());
                System.err.println("Items content: " + response.result.items.toString());
                return new HashMap<>();
            }
        } catch (ClassCastException e) {
            System.err.println("Error casting items: " + e.getMessage());
            System.err.println("Items type: " + response.result.items.getClass().getName());
            System.err.println("Items content: " + response.result.items.toString());
            return new HashMap<>();
        } catch (Exception e) {
            System.err.println("Unexpected error in getItemsAsMap: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Method untuk update API agar item tidak dipanggil lagi
     */
    private void updateProcessedItems(List<String> numbers, String marketplace) {
        System.out.println("=== UPDATE API CALL ===");
        System.out.println("Numbers to mark as processed: " + numbers);
        
        try {
            for (String number : numbers) {
                if (isPaused) break;
                
                System.out.println("Updating picking ID: " + number);
                System.out.println( 
                    String.format("{\"marketplace\":\"%s\",\"picking_id\":\"%s\"}", 
                        marketplace.replace("\"", "\\\""), number.replace("\"", "\\\"")));
                
                // Call update API for each picking ID
                UpdateDOResponse updateResp = DOFetcher.updateDO(
                    "https://aloy.id/warehouse/update/do-resi", 
                    String.format("{\"marketplace\":\"%s\",\"picking_id\":\"%s\"}", 
                        marketplace.replace("\"", "\\\""), number.replace("\"", "\\\""))
                );
                
                System.out.println("Update status for " + number + ": " + updateResp.result.status);
                System.out.println("Update message for " + number + ": " + updateResp.result.msg);
                
                // Small delay between updates
                if (!isPaused) Thread.sleep(100);
            }
            
            System.out.println("API update completed for " + numbers.size() + " items");
            
        } catch (Exception e) {
            System.err.println("Error updating processed items: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=== END UPDATE API CALL ===");
    }

    @FXML
    public void initialize() {
        // Load semua printer
        btnPause.setVisible(false);
        printerCombo.setDisable(false);
        paperCombo.setDisable(false);
        marketplaceCombo.setDisable(false);

        for (PrintService ps : PrintServiceLookup.lookupPrintServices(null, null)) {
            printerCombo.getItems().add(ps.getName());
        }

        // Isi daftar ukuran kertas (contoh basic)
        paperCombo.getItems().addAll(
            // ISO A Series
            new PaperSize("A0", "841 x 1189 mm"),
            new PaperSize("A1", "594 x 841 mm"),
            new PaperSize("A2", "420 x 594 mm"),
            new PaperSize("A3", "297 x 420 mm"),
            new PaperSize("A4", "210 x 297 mm"),
            new PaperSize("A5", "148 x 210 mm"),
            new PaperSize("A6", "105 x 148 mm"),
            new PaperSize("A7", "74 x 105 mm"),
            new PaperSize("A8", "52 x 74 mm"),
            new PaperSize("A9", "37 x 52 mm"),
            new PaperSize("A10", "26 x 37 mm"),

            // ISO B Series
            new PaperSize("B0", "1000 x 1414 mm"),
            new PaperSize("B1", "707 x 1000 mm"),
            new PaperSize("B2", "500 x 707 mm"),
            new PaperSize("B3", "353 x 500 mm"),
            new PaperSize("B4", "250 x 353 mm"),
            new PaperSize("B5", "176 x 250 mm"),
            new PaperSize("B6", "125 x 176 mm"),
            new PaperSize("B7", "88 x 125 mm"),
            new PaperSize("B8", "62 x 88 mm"),
            new PaperSize("B9", "44 x 62 mm"),
            new PaperSize("B10", "31 x 44 mm"),

            // North American Sizes
            new PaperSize("Letter", "8.5 x 11 in"),
            new PaperSize("Legal", "8.5 x 14 in"),
            new PaperSize("Tabloid", "11 x 17 in"),
            new PaperSize("Ledger", "17 x 11 in"),
            new PaperSize("Executive", "7.25 x 10.5 in"),
            new PaperSize("Statement", "5.5 x 8.5 in"),

            // Architect Sizes
            new PaperSize("ARCH A", "9 x 12 in"),
            new PaperSize("ARCH B", "12 x 18 in"),
            new PaperSize("ARCH C", "18 x 24 in"),
            new PaperSize("ARCH D", "24 x 36 in"),
            new PaperSize("ARCH E", "36 x 48 in"),

            // Photo Sizes
            new PaperSize("4R", "4 x 6 in"),
            new PaperSize("5R", "5 x 7 in"),
            new PaperSize("8R", "8 x 10 in"),
            new PaperSize("10R", "10 x 12 in")
        );

        // // Tambahkan marketplace options
        // marketplaceCombo.getItems().addAll("Shopee", "Tokopedia", "Blibli", "Lazada");

        // Set default selections
        if (!printerCombo.getItems().isEmpty()) {
            printerCombo.getSelectionModel().selectFirst();
        }
        paperCombo.getSelectionModel().selectFirst();
        marketplaceCombo.getSelectionModel().selectFirst();
        
        System.out.println("Controller initialized");
        System.out.println("Available printers: " + printerCombo.getItems().size());
    }
}