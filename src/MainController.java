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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainController {

    @FXML private Button btnStart;
    @FXML private Button btnPause;
    @FXML private ComboBox<String> printerCombo;
    @FXML private ComboBox<String> paperCombo;
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
        String selectedPaper = paperCombo.getValue() != null ? paperCombo.getValue() : "";
        String selectedMarketplace = marketplaceCombo.getValue() != null ? marketplaceCombo.getValue() : "";
        String selectedMarketplaceLowercase = selectedMarketplace != null ? selectedMarketplace.toLowerCase() : "";

        Integer limit = 1;

        MediaSizeName tempPaperSize = null;
        if ("A4".equals(selectedPaper)) tempPaperSize = MediaSizeName.ISO_A4;
        else if ("A5".equals(selectedPaper)) tempPaperSize = MediaSizeName.ISO_A5;
        else if ("Letter".equals(selectedPaper)) tempPaperSize = MediaSizeName.NA_LETTER;

        final MediaSizeName paperSize = tempPaperSize;
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
                    
                    DOResponse resp = DOFetcher.fetchDO("http://192.168.1.90:8069/warehouse/get/do-resi", 
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
                            DOResponse pickingResp = DOFetcher.fetchDO("http://192.168.1.90:8069/warehouse/get/do-resi", 
                                String.format("{\"marketplace\":\"%s\",\"limit\":%d,\"start_date\":\"%s\"}", 
                                    selectedMarketplaceLowercase.replace("\"", "\\\""), limit.intValue(), formattedDate.replace("\"", "\\\"")));

                            // Use helper method to safely get items as Map
                            Map<String, String> pickingItems = getItemsAsMap(pickingResp);
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
                                        PDFPrinter.printAndWait(pdfData, selectedPrinter);
                                        PDFPrinter.printAndMonitor(pdfData, selectedPrinter, "invoice_" + number + ".pdf");
                                        PrinterStatus.checkPrinterJobs(selectedPrinter);
                                    } catch (Exception e) {
                                        System.err.println("Error printing document " + number + ": " + e.getMessage());
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
                            DOResponse newPickingResp = DOFetcher.fetchDO("http://192.168.1.90:8069/warehouse/get/do-resi", 
                                String.format("{\"marketplace\":\"%s\",\"limit\":%d,\"start_date\":\"%s\"}", 
                                    selectedMarketplaceLowercase.replace("\"", "\\\""), limit.intValue(), newFormattedDate.replace("\"", "\\\"")));

                            // Use helper method to safely get items as Map
                            Map<String, String> newPickingItems = getItemsAsMap(newPickingResp);
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
                                        PDFPrinter.printAndWait(pdfData, selectedPrinter);
                                        PDFPrinter.printAndMonitor(pdfData, selectedPrinter, "invoice_" + number + ".pdf");
                                        PrinterStatus.checkPrinterJobs(selectedPrinter);
                                    } catch (Exception e) {
                                        System.err.println("Error printing document " + number + ": " + e.getMessage());
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
        isPaused = true;
        
        // Wait for thread to finish
        if (processingThread != null && processingThread.isAlive()) {
            try {
                processingThread.join(5000); // Wait max 5 seconds
            } catch (InterruptedException e) {
                processingThread.interrupt();
            }
        }
        
        btnPause.setVisible(false);
        btnStart.setVisible(true);
        printerCombo.setDisable(false);
        paperCombo.setDisable(false);
        marketplaceCombo.setDisable(false);
        
        System.out.println("Process paused by user");
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
                
                // Call update API for each picking ID
                UpdateDOResponse updateResp = DOFetcher.updateDO(
                    "http://192.168.1.90:8069/warehouse/update/do-status", 
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
        paperCombo.getItems().addAll("A4", "A5", "Letter");

        // Tambahkan marketplace options
        marketplaceCombo.getItems().addAll("Shopee", "Tokopedia", "Blibli", "Lazada");

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