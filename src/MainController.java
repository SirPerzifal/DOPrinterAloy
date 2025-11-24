import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;

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
        private final MediaSizeName mediaSizeName;

        public PaperSize(String name, String dimensions, MediaSizeName mediaSizeName) {
            this.name = name;
            this.dimensions = dimensions;
            this.mediaSizeName = mediaSizeName;
        }

        public String getName() {
            return name;
        }

        public String getDimensions() {
            return dimensions;
        }

        public MediaSizeName getMediaSizeName() {
            return mediaSizeName;
        }

        @Override
        public String toString() {
            return name + " (" + dimensions + ")";
        }
    }

    public static class ShopeeTemplate {
        private final String name;
        private final String key;

        // Fixed constructor name - was "Template" should be "ShopeeTemplate"
        public ShopeeTemplate(String name, String key) {
            this.name = name;
            this.key = key;
        }

        public String getName() {
            return name;
        }

        // Fixed method name - was "getkey" should be "getKey"
        public String getKey() {
            return key;
        }

        @Override
        public String toString() {
            return name;
        }
    }
    
    @FXML private Button btnStart;
    @FXML private Button btnPause;
    @FXML private ComboBox<String> printerCombo;
    @FXML private ComboBox<ShopeeTemplate> shopeeTemplateCombo;
    @FXML private ComboBox<PaperSize> paperCombo;
    @FXML private Button btnPauseLoading; // Button loading baru
    @FXML private ComboBox<String> marketplaceCombo;
    @FXML private VBox shopeeTemplateContainer;

    private volatile boolean isPaused = false;
    private Thread processingThread = null;

    @FXML
    @SuppressWarnings("unchecked")
    private void handleStart() {
        btnStart.setVisible(false);
        btnPause.setVisible(true);
        printerCombo.setDisable(true);
        shopeeTemplateCombo.setDisable(true);
        paperCombo.setDisable(true);
        marketplaceCombo.setDisable(true);

        String selectedPrinter = printerCombo.getValue() != null ? printerCombo.getValue() : "";
        String selectedPaper = paperCombo.getValue() != null ? paperCombo.getValue().getName() : "";
        String selectedShopeetemplate = shopeeTemplateCombo.getValue() != null ? shopeeTemplateCombo.getValue().getKey() : "";
        String selectedMarketplace = marketplaceCombo.getValue() != null ? marketplaceCombo.getValue() : "";
        String selectedMarketplaceLowercase = selectedMarketplace != null ? selectedMarketplace.toLowerCase() : "";

        String selectedShopeeTemplate = "";
        if (shopeeTemplateCombo.getValue() != null) {
            selectedShopeeTemplate = shopeeTemplateCombo.getValue().getKey();
        }

        Integer limit = 1;

        // FIXED: Make it final so it can be used in lambda
        final MediaSizeName selectedPaperSize;
        if (paperCombo.getValue() != null) {
            selectedPaperSize = paperCombo.getValue().getMediaSizeName();
        } else {
            selectedPaperSize = null;
        }

        System.out.println("paper size" + selectedPaper + " " + selectedShopeeTemplate);
        isPaused = false;

        processingThread = new Thread(() -> {
            try {
                // Main processing loop
                while (!isPaused) {
                    System.out.println("=== Starting new cycle ===");

                    if (!isPaused) Thread.sleep(2000);
                    
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
                    
                    String requestJson = createRequestJson(selectedMarketplaceLowercase, limit, formattedDate, selectedShopeetemplate);
                    System.out.println("Picking Data: " + requestJson);
                            // Fetch data for picking with new date
                    DOResponse resp = DOFetcher.fetchDO("https://aloy.id/warehouse/get/do-resi", requestJson);

                    System.out.println("Status   : " + resp.result.status);
                    System.out.println("Message  : " + resp.result.msg);
                    
                    isMore = resp.result.is_more;
                    System.out.println("Is More : " + isMore);

                    // Step 2: Main logic loop - runs forever until paused
                    while (!isPaused) {
                        if (isMore) {
                            System.out.println("Step 2a: is_more = true, fetching picking data...");
                            
                            // Fetch data for picking

                            String pickRequestJson = createRequestJson(selectedMarketplaceLowercase, limit, formattedDate, selectedShopeetemplate);
                            System.out.println("Picking Data: " + pickRequestJson);
                            // Fetch data for picking with new date
                            DOResponse pickingResp = DOFetcher.fetchDO("https://aloy.id/warehouse/get/do-resi", pickRequestJson);

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

                                        // Debug Mode
                                        // PDFDebugSaver.debugPDFComplete(pdfData, number);

                                        PDFPrinterWithStatus.PrintResult result = 
                                            PDFPrinterWithStatus.printWithBookMethod(pdfData, selectedPrinter);
                                        
                                        // Method 1: Try with PrintJobListener (timeout 15s)  
                                        // PDFPrinterWithStatus.PrintResult result = 
                                        //     PDFPrinterWithStatus.printAndWaitWithStatus(pdfData, selectedPrinter, selectedPaperSize, 15000);

                                        if (result.isSuccess()) {
                                            System.out.println("Print berhasil untuk " + number + " (Method 1 with paper size)");
                                        } else {
                                            System.out.println("Method 1 failed untuk " + number + ": " + result.getMessage());
                                            
                                            // Method 2 fallback with paper size
                                            System.out.println("Trying polling method with paper size...");
                                            PDFPrinterWithStatus.PrintResult altResult = 
                                                PDFPrinterWithStatus.printAndWaitWithPolling(pdfData, selectedPrinter, selectedPaperSize, 15000);
                                            
                                            if (altResult.isSuccess()) {
                                                System.out.println("Polling method berhasil untuk " + number + " with paper size");
                                            } else {
                                                System.out.println("Both methods failed untuk " + number);
                                                System.out.println("  - Method 1: " + result.getMessage());
                                                System.out.println("  - Method 2: " + altResult.getMessage());
                                            }
                                        }
                                        
                                    } catch (Exception e) {
                                        System.err.println("Error printing document " + number + ": " + e.getMessage());
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

                            String newRequestJson = createRequestJson(selectedMarketplaceLowercase, limit, formattedDate, selectedShopeetemplate);
                            System.out.println("Picking Data: " + newRequestJson);
                            // Fetch data for picking with new date
                            DOResponse newPickingResp = DOFetcher.fetchDO("https://aloy.id/warehouse/get/do-resi", newRequestJson);

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

                                        // Debug Mode
                                        // PDFDebugSaver.debugPDFComplete(pdfData, number);
                                        
                                        // Method 1: Try with PrintJobListener (timeout 15s)  
                                        // PDFPrinterWithStatus.PrintResult result = 
                                        //     PDFPrinterWithStatus.printAndWaitWithStatus(pdfData, selectedPrinter, selectedPaperSize, 15000);
                                        
                                        PDFPrinterWithStatus.PrintResult result = 
                                            PDFPrinterWithStatus.printWithBookMethod(pdfData, selectedPrinter);

                                        if (result.isSuccess()) {
                                            System.out.println("Print berhasil untuk " + number + " (Method 1 with paper size)");
                                        } else {
                                            System.out.println("Method 1 failed untuk " + number + ": " + result.getMessage());
                                            
                                            // Method 2 fallback with paper size
                                            System.out.println("Trying polling method with paper size...");
                                            PDFPrinterWithStatus.PrintResult altResult = 
                                                PDFPrinterWithStatus.printAndWaitWithPolling(pdfData, selectedPrinter, selectedPaperSize, 15000);
                                            
                                            if (altResult.isSuccess()) {
                                                System.out.println("Polling method berhasil untuk " + number + " with paper size");
                                            } else {
                                                System.out.println("Both methods failed untuk " + number);
                                                System.out.println("  - Method 1: " + result.getMessage());
                                                System.out.println("  - Method 2: " + altResult.getMessage());
                                            }
                                        }
                                        
                                    } catch (Exception e) {
                                        System.err.println("printing document " + number + ": " + e.getMessage());
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

    private String createRequestJson(String marketplace, Integer limit, String startDate, String shopeeRef) {
        // Escape quotes untuk JSON
        String escapedMarketplace = marketplace.replace("\"", "\\\"");
        String escapedStartDate = startDate.replace("\"", "\\\"");
        String escapedShopeeRef = shopeeRef.replace("\"", "\\\"");
        
        // Build JSON string dengan shopee_ref parameter
        return String.format(
            "{\"marketplace\":\"%s\",\"limit\":%d,\"start_date\":\"%s\",\"shopee_ref\":\"%s\"}", 
            escapedMarketplace, 
            limit.intValue(), 
            escapedStartDate, 
            escapedShopeeRef
        );
    }

        // Method to handle marketplace selection change
    @FXML
    private void handleMarketplaceChange() {
        String selectedMarketplace = marketplaceCombo.getValue();
        
        // Show/hide Shopee template based on selection
        if ("Shopee".equals(selectedMarketplace)) {
            shopeeTemplateContainer.setVisible(true);
            shopeeTemplateContainer.setManaged(true);
            
            // FIXED: Explicitly enable the ComboBox when showing
            shopeeTemplateCombo.setDisable(false);
            
            // Also ensure it has a selection if items are available
            if (shopeeTemplateCombo.getItems().isEmpty() == false && 
                shopeeTemplateCombo.getSelectionModel().getSelectedItem() == null) {
                shopeeTemplateCombo.getSelectionModel().selectFirst();
            }
            
            System.out.println("Shopee template section shown and enabled");
        } else {
            shopeeTemplateContainer.setVisible(false);
            shopeeTemplateContainer.setManaged(false);
            
            // Clear selection when hidden to avoid confusion
            shopeeTemplateCombo.getSelectionModel().clearSelection();
            
            System.out.println("Shopee template section hidden for marketplace: " + selectedMarketplace);
        }
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
        
        shopeeTemplateCombo.setDisable(true);

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
                    shopeeTemplateCombo.setDisable(false);
                    
                    System.out.println("Process paused successfully");
                });
            }
        }).start();
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
        btnPause.setVisible(false);
        printerCombo.setDisable(false);
        paperCombo.setDisable(false);
        marketplaceCombo.setDisable(false);
        shopeeTemplateCombo.setDisable(false);

        // Load printers
        for (PrintService ps : PrintServiceLookup.lookupPrintServices(null, null)) {
            printerCombo.getItems().add(ps.getName());
        }

        // Load data from API and populate ComboBoxes
        try {
            // Get data from API
            PaperSizeResponse paperSizeResponse = DOFetcher.GetPaperSize();
            ShopeeTemplateResponse shopeeTemplateResponse = DOFetcher.GetShopeeTemplateFormat();
            
            System.out.println("Paper size data retrieved: " + paperSizeResponse.result.items.size() + " items");
            System.out.println("Shopee template data retrieved: " + shopeeTemplateResponse.result.items.size() + " items");
            
            // Clear existing items (if any)
            paperCombo.getItems().clear();
            shopeeTemplateCombo.getItems().clear();
            
            // Populate Paper Size ComboBox from API
            for (PaperSizeResponse.PaperSizeItem item : paperSizeResponse.result.items) {
                MediaSizeName mediaSizeName = convertStringToMediaSizeName(item.mediaSizeName);
                PaperSize paperSize = new PaperSize(item.name, item.dimensions, mediaSizeName);
                paperCombo.getItems().add(paperSize);
            }
            
            // Populate Shopee Template ComboBox from API
            for (ShopeeTemplateResponse.ShopeeTemplateItem item : shopeeTemplateResponse.result.items) {
                ShopeeTemplate template = new ShopeeTemplate(item.name, item.key);
                shopeeTemplateCombo.getItems().add(template);
            }
            
            System.out.println("ComboBoxes populated successfully from API");
            
        } catch (Exception e) {
            System.err.println("Error retrieving data from API: " + e.getMessage());
            e.printStackTrace();
            
            // Fallback: Use hardcoded values if API fails
            System.out.println("Loading fallback data...");
            loadFallbackData();
        }

        // Set up marketplace change listener
        marketplaceCombo.setOnAction(event -> handleMarketplaceChange());

        // Set default selections
        if (!printerCombo.getItems().isEmpty()) {
            printerCombo.getSelectionModel().selectFirst();
        }
        if (!paperCombo.getItems().isEmpty()) {
            paperCombo.getSelectionModel().selectFirst();
        }
        if (!shopeeTemplateCombo.getItems().isEmpty()) {
            shopeeTemplateCombo.getSelectionModel().selectFirst();
        }
        marketplaceCombo.getSelectionModel().selectFirst();

        // Initialize shopee template visibility
        handleMarketplaceChange();
        
        System.out.println("Controller initialized");
        System.out.println("Available printers: " + printerCombo.getItems().size());
        System.out.println("Available paper sizes: " + paperCombo.getItems().size());
        System.out.println("Available shopee templates: " + shopeeTemplateCombo.getItems().size());
    }

    // Helper method to convert string to MediaSizeName
    private MediaSizeName convertStringToMediaSizeName(String mediaSizeNameString) {
        if (mediaSizeNameString == null || mediaSizeNameString.isEmpty()) {
            return null;
        }
        
        // Remove "MediaSizeName." prefix if present
        String cleanName = mediaSizeNameString.replace("MediaSizeName.", "");
        
        return switch (cleanName) {
            case "ISO_A0" -> MediaSizeName.ISO_A0;
            case "ISO_A1" -> MediaSizeName.ISO_A1;
            case "ISO_A2" -> MediaSizeName.ISO_A2;
            case "ISO_A3" -> MediaSizeName.ISO_A3;
            case "ISO_A4" -> MediaSizeName.ISO_A4;
            case "ISO_A5" -> MediaSizeName.ISO_A5;
            case "ISO_A6" -> MediaSizeName.ISO_A6;
            case "ISO_A7" -> MediaSizeName.ISO_A7;
            case "ISO_A8" -> MediaSizeName.ISO_A8;
            case "ISO_A9" -> MediaSizeName.ISO_A9;
            case "ISO_A10" -> MediaSizeName.ISO_A10;
            case "ISO_B0" -> MediaSizeName.ISO_B0;
            case "ISO_B1" -> MediaSizeName.ISO_B1;
            case "ISO_B2" -> MediaSizeName.ISO_B2;
            case "ISO_B3" -> MediaSizeName.ISO_B3;
            case "ISO_B4" -> MediaSizeName.ISO_B4;
            case "ISO_B5" -> MediaSizeName.ISO_B5;
            case "ISO_B6" -> MediaSizeName.ISO_B6;
            case "ISO_B7" -> MediaSizeName.ISO_B7;
            case "ISO_B8" -> MediaSizeName.ISO_B8;
            case "ISO_B9" -> MediaSizeName.ISO_B9;
            case "ISO_B10" -> MediaSizeName.ISO_B10;
            case "NA_LETTER" -> MediaSizeName.NA_LETTER;
            case "NA_LEGAL" -> MediaSizeName.NA_LEGAL;
            case "TABLOID" -> MediaSizeName.TABLOID;
            case "LEDGER" -> MediaSizeName.LEDGER;
            case "EXECUTIVE" -> MediaSizeName.EXECUTIVE;
            case "INVOICE" -> MediaSizeName.INVOICE;
            case "NA_5X7" -> MediaSizeName.NA_5X7;
            case "NA_8X10" -> MediaSizeName.NA_8X10;
            default -> {
                System.out.println("Unknown MediaSizeName: " + cleanName);
                yield null;
            }
        };
    }

    // Fallback method if API fails
    private void loadFallbackData() {
        // Load hardcoded paper sizes as fallback
        paperCombo.getItems().addAll(
            new PaperSize("A4", "210 x 297 mm", MediaSizeName.ISO_A4),
            new PaperSize("A3", "297 x 420 mm", MediaSizeName.ISO_A3),
            new PaperSize("A5", "148 x 210 mm", MediaSizeName.ISO_A5),
            new PaperSize("Letter", "8.5 x 11 in", MediaSizeName.NA_LETTER)
        );

        // Load hardcoded shopee templates as fallback
        shopeeTemplateCombo.getItems().addAll(
            new ShopeeTemplate("Shopee Picking Slip", "olshop_custom.report_shopee_pickingslip"),
            new ShopeeTemplate("Shopee Picking Slip 100mm x 100mm", "olshop_custom.report_shopee_pickingslip_100_100"),
            new ShopeeTemplate("Shopee Picking Slip 100mm x 120mm", "olshop_custom.report_shopee_pickingslip_100_120"),
            new ShopeeTemplate("Shopee Picking Slip 100mm x 150mm", "olshop_custom.report_shopee_pickingslip_100_150")
        );
    }
}