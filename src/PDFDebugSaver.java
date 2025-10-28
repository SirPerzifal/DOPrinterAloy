import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.ByteArrayInputStream;

public class PDFDebugSaver {
    
    private static final String DEFAULT_SAVE_DIR = "pdf_debug_output";
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    /**
     * Saves the original decoded Base64 PDF (before PDFBox processing)
     */
    public static Path saveOriginalPDF(byte[] pdfBytes, String pickingNumber, String customDir) {
        try {
            String saveDir = (customDir != null && !customDir.isEmpty()) ? customDir : DEFAULT_SAVE_DIR;
            Path dirPath = Paths.get(saveDir);
            
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                System.out.println("Created debug directory: " + dirPath.toAbsolutePath());
            }
            
            String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
            String sanitizedPickingNumber = pickingNumber.replaceAll("[^a-zA-Z0-9_-]", "_");
            String filename = String.format("%s_%s_ORIGINAL.pdf", sanitizedPickingNumber, timestamp);
            
            Path filePath = dirPath.resolve(filename);
            
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(pdfBytes);
                fos.flush();
            }
            
            System.out.println("✓ Original PDF saved: " + filePath.toAbsolutePath());
            return filePath;
            
        } catch (IOException e) {
            System.err.println("✗ Failed to save original PDF: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Saves PDF after being processed by PDFBox (loaded and re-saved)
     * This helps identify if PDFBox is causing the issue
     */
    public static Path savePDFBoxProcessed(byte[] pdfBytes, String pickingNumber, String customDir) {
        try {
            String saveDir = (customDir != null && !customDir.isEmpty()) ? customDir : DEFAULT_SAVE_DIR;
            Path dirPath = Paths.get(saveDir);
            
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            
            String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
            String sanitizedPickingNumber = pickingNumber.replaceAll("[^a-zA-Z0-9_-]", "_");
            String filename = String.format("%s_%s_PDFBOX_PROCESSED.pdf", sanitizedPickingNumber, timestamp);
            
            Path filePath = dirPath.resolve(filename);
            
            // Load with PDFBox (same way as printing) and save
            try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
                document.save(filePath.toFile());
                System.out.println("✓ PDFBox processed PDF saved: " + filePath.toAbsolutePath());
                System.out.println("  Pages in document: " + document.getNumberOfPages());
                return filePath;
            }
            
        } catch (Exception e) {
            System.err.println("✗ Failed to save PDFBox processed PDF: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * NEW: Save PDDocument that's already loaded
     * Use this to save the exact document that will be printed
     */
    public static Path savePDDocumentDirect(PDDocument document, String pickingNumber, String customDir) {
        try {
            String saveDir = (customDir != null && !customDir.isEmpty()) ? customDir : DEFAULT_SAVE_DIR;
            Path dirPath = Paths.get(saveDir);
            
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            
            String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
            String sanitizedPickingNumber = pickingNumber.replaceAll("[^a-zA-Z0-9_-]", "_");
            String filename = String.format("%s_%s_LOADED_DOCUMENT.pdf", sanitizedPickingNumber, timestamp);
            
            Path filePath = dirPath.resolve(filename);
            
            // Save the already-loaded document
            document.save(filePath.toFile());
            System.out.println("✓ Loaded PDDocument saved: " + filePath.toAbsolutePath());
            System.out.println("  Pages in document: " + document.getNumberOfPages());
            System.out.println("  This is the EXACT document that will be printed");
            
            return filePath;
            
        } catch (Exception e) {
            System.err.println("✗ Failed to save loaded PDDocument: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Shortcut with default directory
     */
    public static Path savePDDocumentDirect(PDDocument document, String pickingNumber) {
        return savePDDocumentDirect(document, pickingNumber, null);
    }
    
    /**
     * Comprehensive debug - saves both versions and compares
     */
    public static void debugPDFComplete(byte[] pdfBytes, String pickingNumber, String customDir) {
        System.out.println("=== PDF Debug Analysis for: " + pickingNumber + " ===");
        
        // Save original
        Path originalPath = saveOriginalPDF(pdfBytes, pickingNumber, customDir);
        if (originalPath != null) {
            validatePDF(originalPath, "ORIGINAL");
            analyzeOriginalPDF(pdfBytes);
        }
        
        // Save PDFBox processed
        Path processedPath = savePDFBoxProcessed(pdfBytes, pickingNumber, customDir);
        if (processedPath != null) {
            validatePDF(processedPath, "PDFBOX_PROCESSED");
        }
        
        // Compare file sizes
        if (originalPath != null && processedPath != null) {
            try {
                long originalSize = Files.size(originalPath);
                long processedSize = Files.size(processedPath);
                System.out.println("File size comparison:");
                System.out.println("  Original: " + originalSize + " bytes");
                System.out.println("  Processed: " + processedSize + " bytes");
                System.out.println("  Difference: " + (processedSize - originalSize) + " bytes");
            } catch (IOException e) {
                System.err.println("Could not compare file sizes");
            }
        }
        
        System.out.println("=== End PDF Debug Analysis ===\n");
    }
    
    /**
     * Quick save with default directory
     */
    public static void debugPDFComplete(byte[] pdfBytes, String pickingNumber) {
        debugPDFComplete(pdfBytes, pickingNumber, null);
    }
    
    /**
     * Analyzes the original PDF without PDFBox processing
     */
    private static void analyzeOriginalPDF(byte[] pdfBytes) {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            System.out.println("Original PDF Analysis:");
            System.out.println("  Total pages: " + doc.getNumberOfPages());
            System.out.println("  Is encrypted: " + doc.isEncrypted());
            System.out.println("  PDF version: " + doc.getVersion());
            
            // Check each page
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                var page = doc.getPage(i);
                var mediaBox = page.getMediaBox();
                System.out.println("  Page " + (i+1) + " size: " + 
                    mediaBox.getWidth() + " x " + mediaBox.getHeight() + " points");
            }
        } catch (Exception e) {
            System.err.println("Could not analyze original PDF: " + e.getMessage());
        }
    }
    
    /**
     * Validates PDF file
     */
    private static boolean validatePDF(Path pdfPath, String label) {
        try {
            byte[] header = new byte[5];
            try (var fis = Files.newInputStream(pdfPath)) {
                int read = fis.read(header);
                if (read < 5) {
                    System.err.println("✗ [" + label + "] PDF file too small");
                    return false;
                }
            }
            
            String headerStr = new String(header);
            if (!headerStr.startsWith("%PDF-")) {
                System.err.println("✗ [" + label + "] Invalid PDF header");
                return false;
            }
            
            long fileSize = Files.size(pdfPath);
            System.out.println("✓ [" + label + "] Valid PDF, size: " + fileSize + " bytes");
            return true;
            
        } catch (IOException e) {
            System.err.println("✗ [" + label + "] Failed to validate: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets info about the save directory
     */
    public static void printDirectoryInfo(String customDir) {
        String saveDir = (customDir != null && !customDir.isEmpty()) ? customDir : DEFAULT_SAVE_DIR;
        Path dirPath = Paths.get(saveDir).toAbsolutePath();
        
        System.out.println("=== PDF Debug Directory Info ===");
        System.out.println("Directory: " + dirPath);
        System.out.println("Exists: " + Files.exists(dirPath));
        
        if (Files.exists(dirPath)) {
            try {
                long fileCount = Files.list(dirPath)
                    .filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .count();
                System.out.println("PDF files: " + fileCount);
            } catch (IOException e) {
                System.err.println("Error counting files: " + e.getMessage());
            }
        }
        System.out.println("================================");
    }
}