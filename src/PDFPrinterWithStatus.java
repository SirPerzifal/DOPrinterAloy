// PDFPrinterWithStatus.java - Simplified Version
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterJob;

import javax.print.attribute.standard.MediaSizeName;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Chromaticity;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.PrintQuality;
import javax.print.attribute.standard.Sides;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;
import org.apache.pdfbox.rendering.PDFRenderer;


public class PDFPrinterWithStatus {
    
    private static void checkPrinterStatus(PrintService printer) {
        System.out.println("=== Checking Printer Status ===");
        System.out.println("Printer: " + printer.getName());
        
        try {
            // Get printer attributes
            PrintServiceAttributeSet attributes = printer.getAttributes();
            
            // Check printer state
            PrinterState state = (PrinterState) attributes.get(PrinterState.class);
            PrinterStateReasons reasons = (PrinterStateReasons) attributes.get(PrinterStateReasons.class);
            QueuedJobCount jobCount = (QueuedJobCount) attributes.get(QueuedJobCount.class);
            
            System.out.println("Printer State: " + (state != null ? state : "Unknown"));
            System.out.println("Queued Jobs: " + (jobCount != null ? jobCount.getValue() : "Unknown"));
            
            // Check if printer is accepting jobs using attribute instead of method
            PrinterIsAcceptingJobs acceptingJobs = (PrinterIsAcceptingJobs) attributes.get(PrinterIsAcceptingJobs.class);
            System.out.println("Accepting Jobs: " + (acceptingJobs != null ? acceptingJobs : "Unknown"));
            
            if (acceptingJobs != null && acceptingJobs == PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS) {
                System.out.println("    >>> PRINTER NOT ACCEPTING JOBS! <<<");
            }
            
            // Check specific printer issues
            if (reasons != null && !reasons.isEmpty()) {
                System.out.println("Printer Issues Found:");
                for (PrinterStateReason reason : reasons.keySet()) {
                    Severity severity = reasons.get(reason);
                    System.out.println("  - " + reason + " (Severity: " + severity + ")");
                    
                    // Check for specific problems
                    if (reason == PrinterStateReason.MEDIA_EMPTY) {
                        System.out.println("    >>> PAPER IS EMPTY! <<<");
                    } else if (reason == PrinterStateReason.MEDIA_LOW) {
                        System.out.println("    >>> PAPER IS LOW! <<<");
                    } else if (reason == PrinterStateReason.TONER_EMPTY) {
                        System.out.println("    >>> TONER/INK IS EMPTY! <<<");
                    } else if (reason == PrinterStateReason.TONER_LOW) {
                        System.out.println("    >>> TONER/INK IS LOW! <<<");
                    } else if (reason == PrinterStateReason.DOOR_OPEN) {
                        System.out.println("    >>> PRINTER DOOR IS OPEN! <<<");
                    } else if (reason == PrinterStateReason.MEDIA_JAM) {
                        System.out.println("    >>> PAPER JAM! <<<");
                    }
                }
            } else {
                System.out.println("No printer issues reported");
            }
            
        } catch (Exception e) {
            System.out.println("Error checking printer status: " + e.getMessage());
        }
        
        System.out.println("=== End Printer Status ===");
    }

    public enum PrintStatus {
        SUCCESS, FAILED, TIMEOUT, CANCELLED, UNKNOWN
    }
    
    public static class PrintResult {
        private PrintStatus status;
        private String message;
        private Throwable exception;
        
        public PrintResult(PrintStatus status, String message) {
            this.status = status;
            this.message = message;
        }
        
        public PrintResult(PrintStatus status, String message, Throwable exception) {
            this.status = status;
            this.message = message;
            this.exception = exception;
        }
        
        public PrintStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public Throwable getException() { return exception; }
        public boolean isSuccess() { return status == PrintStatus.SUCCESS; }
    }
    
    public static PrintResult printAndWaitWithStatus(byte[] pdfBytes, MediaSizeName paperSize, String printerName) {
        return printAndWaitWithStatus(pdfBytes, printerName, paperSize, 15000);
    }
    
    public static PrintResult printAndWaitWithStatus(byte[] pdfBytes, String printerName, MediaSizeName paperSize, int timeoutMs) {
        System.out.println("Starting enhanced print with comprehensive status monitoring...");

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            // Find printer - FIXED: Make it final
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            final PrintService targetPrinter; // Declare as final
            
            // Find printer in a way that allows final assignment
            PrintService foundPrinter = null;
            for (PrintService ps : services) {
                if (ps.getName().equalsIgnoreCase(printerName)) {
                    foundPrinter = ps;
                    break;
                }
            }
            
            // Final assignment
            targetPrinter = foundPrinter;
            
            if (targetPrinter == null) {
                return new PrintResult(PrintStatus.FAILED, "Printer not found: " + printerName);
            }
            
            // Check printer status BEFORE printing
            checkPrinterStatus(targetPrinter);
            
            // Check if printer is accepting jobs using attribute
            PrintServiceAttributeSet preAttributes = targetPrinter.getAttributes();
            PrinterIsAcceptingJobs acceptingJobs = (PrinterIsAcceptingJobs) preAttributes.get(PrinterIsAcceptingJobs.class);
            
            if (acceptingJobs != null && acceptingJobs == PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS) {
                return new PrintResult(PrintStatus.FAILED, "Printer is not accepting jobs");
            }
            
            // Check for critical issues
            PrinterStateReasons preReasons = (PrinterStateReasons) preAttributes.get(PrinterStateReasons.class);
            
            if (preReasons != null) {
                for (PrinterStateReason reason : preReasons.keySet()) {
                    Severity severity = preReasons.get(reason);
                    
                    // Block printing if critical issues exist
                    if (severity == Severity.ERROR) {
                        String errorMsg = "Critical printer error: " + reason;
                        System.out.println(errorMsg);
                        
                        if (reason == PrinterStateReason.MEDIA_EMPTY) {
                            return new PrintResult(PrintStatus.FAILED, "Cannot print: Paper is empty");
                        } else if (reason == PrinterStateReason.TONER_EMPTY) {
                            return new PrintResult(PrintStatus.FAILED, "Cannot print: Toner/Ink is empty");
                        } else if (reason == PrinterStateReason.MEDIA_JAM) {
                            return new PrintResult(PrintStatus.FAILED, "Cannot print: Paper jam detected");
                        } else if (reason == PrinterStateReason.DOOR_OPEN) {
                            return new PrintResult(PrintStatus.FAILED, "Cannot print: Printer door is open");
                        } else {
                            return new PrintResult(PrintStatus.FAILED, errorMsg);
                        }
                    }
                }
            }
            
            System.out.println("Printer status OK, proceeding with print job...");

            PrinterJob printerJob = PrinterJob.getPrinterJob();
            printerJob.setPrintService(targetPrinter);

            PrintRequestAttributeSet printAttributes = new HashPrintRequestAttributeSet();
            
            // Set paper size
            if (paperSize != null) {
                printAttributes.add(paperSize);
                System.out.println("✓ Paper size set: " + paperSize);
            }
            
            // CRITICAL: Set print quality to HIGH
            printAttributes.add(PrintQuality.HIGH);
            System.out.println("✓ Print quality set: HIGH");
            
            // Set color mode (if your printer supports color)
            printAttributes.add(Chromaticity.COLOR);
            System.out.println("✓ Color mode set: COLOR");
            
            // Set copies
            printAttributes.add(new Copies(1));
            
            // Set orientation (Portrait by default, adjust if needed)
            printAttributes.add(OrientationRequested.PORTRAIT);
            System.out.println("✓ Orientation set: PORTRAIT");
            
            // IMPORTANT: Use ACTUAL_SIZE scaling instead of SHRINK_TO_FIT
            // This prevents borders from being cut off
            System.out.println("✓ Using ACTUAL_SIZE scaling (no shrinking)");
            
            // Create page format with proper margins
            PageFormat pageFormat = printerJob.defaultPage();
            Paper paper = pageFormat.getPaper();
            
            // Set minimal margins (in points, 72 points = 1 inch)
            // Adjust these if borders are still cut off
            // double margin = 10.0; // 10 points = ~3.5mm margin
            // paper.setImageableArea(
            //     margin, 
            //     margin, 
            //     paper.getWidth() - 2 * margin, 
            //     paper.getHeight() - 2 * margin
            // );
            // pageFormat.setPaper(paper);
            
            System.out.println("✓ Page margins set: " + margin + " points");
            System.out.println("  Imageable area: " + paper.getImageableWidth() + " x " + paper.getImageableHeight());
            
            // Use PDFPageable for better page handling (prevents blank pages)
            PDFPageable pageable = new PDFPageable(document);
            printerJob.setPageable(pageable);
            
            System.out.println("✓ Document has " + document.getNumberOfPages() + " pages");
            System.out.println("✓ Using PDFPageable (better page handling)");

            try {
                // Print with attributes
                printerJob.print(printAttributes);
                
                System.out.println("✓ Print job submitted successfully with HIGH QUALITY settings");
                
                // Give some time for the job to process
                Thread.sleep(2000);
                
                // Check final printer status
                checkPrinterStatus(targetPrinter);
                
                return new PrintResult(PrintStatus.SUCCESS, "Print completed with high quality settings");
                
            } catch (Exception e) {
                System.err.println("✗ Print failed: " + e.getMessage());
                e.printStackTrace();
                checkPrinterStatus(targetPrinter);
                return new PrintResult(PrintStatus.FAILED, "Print exception: " + e.getMessage(), e);
            }

            // =========================== OLD ONE ===========================
            
            // Create print request attributes
            // PrintRequestAttributeSet printAttributes = new HashPrintRequestAttributeSet();
            // if (paperSize != null) {
            //     printAttributes.add(paperSize);
            //     System.out.println("Paper size set to: " + paperSize);
            // }
            // printAttributes.add(new Copies(1));
            
            // CountDownLatch printLatch = new CountDownLatch(1);
            // PrintResult[] result = {null};
            
            // DocPrintJob docJob = targetPrinter.createPrintJob();
            
            // Enhanced listener with status checking during print
            // docJob.addPrintJobListener(new PrintJobListener() {
            //     private boolean dataTransferDone = false;
            //     private boolean noMoreEventsCalled = false;
                
            //     @Override
            //     public void printJobCompleted(PrintJobEvent pje) {
            //         System.out.println("Print job completed successfully");
            //         // Check final printer status - now targetPrinter is final
            //         checkPrinterStatus(targetPrinter);
            //         result[0] = new PrintResult(PrintStatus.SUCCESS, "Print completed successfully");
            //         printLatch.countDown();
            //     }

            //     @Override
            //     public void printJobFailed(PrintJobEvent pje) {
            //         System.out.println("Print job failed");
            //         // Check why it failed - now targetPrinter is final
            //         checkPrinterStatus(targetPrinter);
                    
            //         String failureReason = "Print job failed";
            //         PrintServiceAttributeSet attrs = targetPrinter.getAttributes();
            //         PrinterStateReasons reasons = (PrinterStateReasons) attrs.get(PrinterStateReasons.class);
                    
            //         if (reasons != null) {
            //             for (PrinterStateReason reason : reasons.keySet()) {
            //                 if (reasons.get(reason) == Severity.ERROR) {
            //                     failureReason = "Print failed due to: " + reason;
            //                     break;
            //                 }
            //             }
            //         }
                    
            //         result[0] = new PrintResult(PrintStatus.FAILED, failureReason);
            //         printLatch.countDown();
            //     }

            //     @Override
            //     public void printJobCanceled(PrintJobEvent pje) {
            //         System.out.println("Print job cancelled");
            //         checkPrinterStatus(targetPrinter);
            //         result[0] = new PrintResult(PrintStatus.CANCELLED, "Print job cancelled");
            //         printLatch.countDown();
            //     }

            //     @Override
            //     public void printJobRequiresAttention(PrintJobEvent pje) {
            //         System.out.println("Print job requires attention");
            //         checkPrinterStatus(targetPrinter);
            //         result[0] = new PrintResult(PrintStatus.FAILED, "Print job requires attention - check printer");
            //         printLatch.countDown();
            //     }

            //     @Override
            //     public void printDataTransferCompleted(PrintJobEvent pje) {
            //         System.out.println("Data transfer completed");
            //         dataTransferDone = true;
            //         checkCompletion();
            //     }

            //     @Override
            //     public void printJobNoMoreEvents(PrintJobEvent pje) {
            //         System.out.println("No more print events");
            //         noMoreEventsCalled = true;
            //         checkCompletion();
            //     }
                
            //     private void checkCompletion() {
            //         if (dataTransferDone && noMoreEventsCalled && result[0] == null) {
            //             System.out.println("Assuming success based on data transfer + no more events");
            //             // Final status check - now targetPrinter is final
            //             checkPrinterStatus(targetPrinter);
            //             result[0] = new PrintResult(PrintStatus.SUCCESS, 
            //                 "Print completed (inferred from events)");
            //             printLatch.countDown();
            //         }
            //     }
            // });
            
            // try {
            //     // Submit print job
            //     Doc doc = new SimpleDoc(new PDFPrintable(document, Scaling.ACTUAL_SIZE), 
            //                         DocFlavor.SERVICE_FORMATTED.PRINTABLE, null);
            //     docJob.print(doc, printAttributes);
            //     System.out.println("Print job submitted, monitoring status...");
                
            //     // Monitor printer status during print (in background)
            //     Thread statusMonitor = new Thread(() -> {
            //         try {
            //             for (int i = 0; i < timeoutMs / 2000; i++) {
            //                 Thread.sleep(2000);
            //                 if (result[0] != null) break; // Print finished
                            
            //                 System.out.println("--- Status Check During Print ---");
            //                 checkPrinterStatus(targetPrinter); // now targetPrinter is final
            //             }
            //         } catch (InterruptedException e) {
            //             // Monitor interrupted, normal
            //         }
            //     });
            //     statusMonitor.start();
                
            //     // Wait for completion
            //     boolean completed = printLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            //     statusMonitor.interrupt(); // Stop monitoring
                
            //     if (completed && result[0] != null) {
            //         return result[0];
            //     } else {
            //         System.out.println("Print operation timed out");
            //         checkPrinterStatus(targetPrinter); // Final check on timeout - now targetPrinter is final
            //         return new PrintResult(PrintStatus.TIMEOUT, "Print operation timed out after " + timeoutMs + "ms");
            //     }
                
            // } catch (PrintException e) {
            //     System.out.println("Print exception: " + e.getMessage());
            //     checkPrinterStatus(targetPrinter);
            //     return new PrintResult(PrintStatus.FAILED, "Print exception: " + e.getMessage(), e);
            // } catch (Exception e) {
            //     System.out.println("Exception during printing: " + e.getMessage());
            //     checkPrinterStatus(targetPrinter);
            //     return new PrintResult(PrintStatus.FAILED, "Exception during printing", e);
            // }
            
        } catch (Exception e) {
            System.out.println("Failed to load PDF: " + e.getMessage());
            return new PrintResult(PrintStatus.FAILED, "Failed to load PDF: " + e.getMessage(), e);
        }
    }

    public static PrintResult printRawPDF(byte[] pdfBytes, String printerName, MediaSizeName paperSize) {
        System.out.println("=== RAW PDF PRINT (No Rendering) ===");
        System.out.println("Printer: " + printerName);
        System.out.println("Paper Size: " + paperSize);
        System.out.println("PDF Size: " + pdfBytes.length + " bytes");
        
        try {
            // Find printer
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            PrintService targetPrinter = null;
            
            for (PrintService ps : services) {
                if (ps.getName().equalsIgnoreCase(printerName)) {
                    targetPrinter = ps;
                    break;
                }
            }
            
            if (targetPrinter == null) {
                return new PrintResult(PrintStatus.FAILED, "Printer not found: " + printerName);
            }
            
            System.out.println("✓ Printer found: " + targetPrinter.getName());
            
            // Check if printer supports PDF
            DocFlavor pdfFlavor = DocFlavor.INPUT_STREAM.PDF;
            if (!targetPrinter.isDocFlavorSupported(pdfFlavor)) {
                System.out.println("⚠ Printer does not support direct PDF printing");
                System.out.println("  Supported flavors:");
                DocFlavor[] flavors = targetPrinter.getSupportedDocFlavors();
                for (DocFlavor flavor : flavors) {
                    System.out.println("    - " + flavor);
                }
                return new PrintResult(PrintStatus.FAILED, "Printer does not support PDF format");
            }
            
            System.out.println("✓ Printer supports direct PDF printing");
            
            // Create print job
            DocPrintJob printJob = targetPrinter.createPrintJob();
            
            // Set print attributes
            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            
            if (paperSize != null) {
                attributes.add(paperSize);
                System.out.println("✓ Paper size: " + paperSize);
            }
            
            // Add other quality attributes
            attributes.add(PrintQuality.HIGH);
            attributes.add(new Copies(1));
            attributes.add(Chromaticity.COLOR);
            attributes.add(OrientationRequested.PORTRAIT);
            
            System.out.println("✓ Print quality: HIGH");
            System.out.println("✓ Color mode: COLOR");
            
            // Create Doc from PDF bytes
            ByteArrayInputStream pdfStream = new ByteArrayInputStream(pdfBytes);
            Doc doc = new SimpleDoc(pdfStream, pdfFlavor, null);
            
            System.out.println("✓ PDF document created");
            
            // Print
            try {
                printJob.print(doc, attributes);
                System.out.println("✓ Print job submitted successfully (RAW PDF)");
                System.out.println("  This method preserves all PDF features including borders!");
                
                // Small delay
                Thread.sleep(1000);
                
                return new PrintResult(PrintStatus.SUCCESS, "Raw PDF printed successfully");
                
            } catch (PrintException e) {
                System.err.println("✗ Print failed: " + e.getMessage());
                e.printStackTrace();
                return new PrintResult(PrintStatus.FAILED, "Print exception: " + e.getMessage(), e);
            }
            
        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
            return new PrintResult(PrintStatus.FAILED, "Error: " + e.getMessage(), e);
        }
    }

    public static PrintResult printWithBookMethod(byte[] pdfBytes, String printerName) {
        System.setProperty("org.apache.pdfbox.rendering.UsePureJavaCMYKConversion", "true");

        try {
            // === STEP 1: Flatten PDF agar tabel dan garis muncul ===
            PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes));
            PDFRenderer renderer = new PDFRenderer(doc);

            PDDocument flattened = new PDDocument();
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                // render halaman jadi gambar resolusi tinggi
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                PDPage newPage = new PDPage(doc.getPage(i).getMediaBox());
                flattened.addPage(newPage);

                PDPageContentStream cs = new PDPageContentStream(flattened, newPage);
                PDImageXObject ximage = LosslessFactory.createFromImage(flattened, image);
                cs.drawImage(ximage, 0, 0, 
                    newPage.getMediaBox().getWidth(), 
                    newPage.getMediaBox().getHeight());
                cs.close();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            flattened.save(out);
            flattened.close();
            doc.close();

            byte[] flattenedBytes = out.toByteArray();

            // === STEP 2: Setup printer dan layout ===
            try (PDDocument document = PDDocument.load(new ByteArrayInputStream(flattenedBytes))) {
                PrintService targetPrinter = Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                    .filter(ps -> ps.getName().equalsIgnoreCase(printerName))
                    .findFirst().orElse(null);

                if (targetPrinter == null)
                    return new PrintResult(PrintStatus.FAILED, "Printer not found: " + printerName);

                PrinterJob printerJob = PrinterJob.getPrinterJob();
                printerJob.setPrintService(targetPrinter);

                // Render as image (DPI tinggi untuk kualitas)
                PDFPrintable printable = new PDFPrintable(document, Scaling.SHRINK_TO_FIT, true, 600);

                // Setup ukuran kertas 100x120 mm
                PageFormat pageFormat = printerJob.defaultPage();
                Paper paper = new Paper();
                double paperWidth = 100 * 2.83465;   // mm → point
                double paperHeight = 120 * 2.83465;  // mm → point
                paper.setSize(paperWidth, paperHeight);

                // Tambahkan margin atas (biar gak turun)
                double margin = 5 * 2.83465;
                paper.setImageableArea(margin, margin, 
                    paperWidth - 2 * margin, paperHeight - 2 * margin);

                pageFormat.setPaper(paper);
                printerJob.setPrintable(printable, pageFormat);

                PrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();
                attr.add(PrintQuality.HIGH);
                attr.add(Chromaticity.COLOR);
                attr.add(new Copies(1));
                attr.add(Sides.ONE_SIDED);

                // === STEP 3: Print dokumen ===
                printerJob.print(attr);

                return new PrintResult(PrintStatus.SUCCESS, "Print completed successfully (flattened)");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new PrintResult(PrintStatus.FAILED, "Failed: " + e.getMessage(), e);
        }
    }
    
    public static PrintResult printAndWaitWithPolling(byte[] pdfBytes, String printerName, MediaSizeName paperSize, int timeoutMs) {
        System.out.println("Starting print with polling method and paper size: " + paperSize);
        
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PrinterJob job = PrinterJob.getPrinterJob();

            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            PrintService targetPrinter = null;
            for (PrintService ps : services) {
                if (ps.getName().equalsIgnoreCase(printerName)) {
                    targetPrinter = ps;
                    break;
                }
            }
            if (targetPrinter == null) {
                return new PrintResult(PrintStatus.FAILED, "Printer not found: " + printerName);
            }
            
            job.setPrintService(targetPrinter);
            
            // Use PDFPageable instead
            PageFormat pageFormat = job.defaultPage();
            Paper paper = pageFormat.getPaper();
            double margin = 3.0;
            paper.setImageableArea(margin, margin, 
                paper.getWidth() - 2 * margin, 
                paper.getHeight() - 2 * margin);
            pageFormat.setPaper(paper);
            
            PDFPageable pageable = new PDFPageable(document, pageFormat, Scaling.SHRINK_TO_FIT);
            job.setPageable(pageable);
            
            PrintRequestAttributeSet printAttributes = new HashPrintRequestAttributeSet();
            if (paperSize != null) {
                printAttributes.add(paperSize);
            }
            printAttributes.add(new Copies(1));
            printAttributes.add(Sides.ONE_SIDED);
            
            try {
                job.print(printAttributes);
                System.out.println("Print command sent successfully with paper size: " + paperSize);
            } catch (Exception e) {
                return new PrintResult(PrintStatus.FAILED, "Print execution failed: " + e.getMessage(), e);
            }
            
            // Polling logic
            int intervalMs = 2000;
            int waited = 0;
            
            while (waited < timeoutMs) {
                Thread.sleep(intervalMs);
                waited += intervalMs;
                
                PrintServiceAttributeSet attrs = targetPrinter.getAttributes();
                PrinterState state = (PrinterState) attrs.get(PrinterState.class);
                QueuedJobCount queueCount = (QueuedJobCount) attrs.get(QueuedJobCount.class);
                
                System.out.println(String.format("[%ds] State: %s, Queue: %s", 
                                                waited/1000, state, 
                                                queueCount != null ? queueCount.getValue() : "unknown"));
                
                if (waited >= 5000) {
                    System.out.println("Assuming print completed after " + waited + "ms wait");
                    return new PrintResult(PrintStatus.SUCCESS, "Print likely completed (polling timeout)");
                }
            }
            
            return new PrintResult(PrintStatus.TIMEOUT, "Polling timeout");
            
        } catch (Exception e) {
            return new PrintResult(PrintStatus.FAILED, "Failed to process PDF: " + e.getMessage(), e);
        }
    }

    /**
     * ALTERNATIVE METHOD - Using custom PageFormat with proper margins
     */
    public static PrintResult printWithCustomPageFormat(byte[] pdfBytes, String printerName, MediaSizeName paperSize) {
        System.out.println("=== Starting Custom PageFormat Method ===");
        
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            PrintService targetPrinter = null;
            for (PrintService ps : services) {
                if (ps.getName().equalsIgnoreCase(printerName)) {
                    targetPrinter = ps;
                    break;
                }
            }
            
            if (targetPrinter == null) {
                return new PrintResult(PrintStatus.FAILED, "Printer not found: " + printerName);
            }
            
            PrinterJob printerJob = PrinterJob.getPrinterJob();
            printerJob.setPrintService(targetPrinter);
            
            // Create proper page format
            PageFormat pageFormat = printerJob.defaultPage();
            Paper paper = pageFormat.getPaper();
            
            // Set minimal margins to prevent right-side cutoff
            double margin = 5.0; // Very small margin (about 1.8mm)
            paper.setImageableArea(
                margin, 
                margin, 
                paper.getWidth() - (2 * margin), 
                paper.getHeight() - (2 * margin)
            );
            pageFormat.setPaper(paper);
            
            // Use SHRINK_TO_FIT to prevent table cutoff
            PDFPrintable printable = new PDFPrintable(document, Scaling.SHRINK_TO_FIT);
            printerJob.setPrintable(printable, pageFormat);
            
            // Setup print attributes
            PrintRequestAttributeSet printAttributes = new HashPrintRequestAttributeSet();
            
            if (paperSize != null) {
                printAttributes.add(paperSize);
            }
            
            printAttributes.add(PrintQuality.HIGH);
            printAttributes.add(Chromaticity.COLOR);
            printAttributes.add(Sides.ONE_SIDED);
            
            System.out.println("Printing with SHRINK_TO_FIT scaling");
            
            try {
                printerJob.print(printAttributes);
                System.out.println("✓ Custom PageFormat print successful");
                return new PrintResult(PrintStatus.SUCCESS, "Print completed with custom format");
            } catch (Exception e) {
                System.err.println("✗ Custom PageFormat failed: " + e.getMessage());
                return new PrintResult(PrintStatus.FAILED, "Custom format failed: " + e.getMessage(), e);
            }
            
        } catch (Exception e) {
            return new PrintResult(PrintStatus.FAILED, "Failed to load PDF: " + e.getMessage(), e);
        }
    }
    
    /**
     * RECOMMENDED METHOD - Combines best practices
     */
    public static PrintResult printRecommended(byte[] pdfBytes, String printerName, MediaSizeName paperSize) {
        System.out.println("=== Starting RECOMMENDED Print Method ===");
        
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            PrintService targetPrinter = null;
            for (PrintService ps : services) {
                if (ps.getName().equalsIgnoreCase(printerName)) {
                    targetPrinter = ps;
                    break;
                }
            }
            
            if (targetPrinter == null) {
                return new PrintResult(PrintStatus.FAILED, "Printer not found: " + printerName);
            }
            
            PrinterJob printerJob = PrinterJob.getPrinterJob();
            printerJob.setPrintService(targetPrinter);
            
            // Get default page format
            PageFormat pageFormat = printerJob.defaultPage();
            Paper paper = pageFormat.getPaper();
            
            // Set very small margins
            double marginPoints = 3.0; // ~1mm margin
            paper.setImageableArea(
                marginPoints, 
                marginPoints, 
                paper.getWidth() - (2 * marginPoints), 
                paper.getHeight() - (2 * marginPoints)
            );
            pageFormat.setPaper(paper);
            
            // Use PDFPageable with the custom page format
            PDFPageable pageable = new PDFPageable(document, pageFormat, Scaling.SHRINK_TO_FIT);
            printerJob.setPageable(pageable);
            
            // Setup print attributes
            PrintRequestAttributeSet printAttributes = new HashPrintRequestAttributeSet();
            
            if (paperSize != null) {
                printAttributes.add(paperSize);
                System.out.println("Paper size: " + paperSize);
            }
            
            printAttributes.add(PrintQuality.HIGH);
            printAttributes.add(Chromaticity.COLOR);
            printAttributes.add(Sides.ONE_SIDED);
            printAttributes.add(new Copies(1));
            
            // Explicitly set page range
            printAttributes.add(new PageRanges(1, document.getNumberOfPages()));
            
            System.out.println("Document pages: " + document.getNumberOfPages());
            System.out.println("Using SHRINK_TO_FIT with minimal margins");
            
            try {
                printerJob.print(printAttributes);
                System.out.println("✓ Recommended method successful");
                return new PrintResult(PrintStatus.SUCCESS, "Print completed successfully");
            } catch (Exception e) {
                System.err.println("✗ Recommended method failed: " + e.getMessage());
                e.printStackTrace();
                return new PrintResult(PrintStatus.FAILED, "Print failed: " + e.getMessage(), e);
            }
            
        } catch (Exception e) {
            System.err.println("✗ Failed to load PDF: " + e.getMessage());
            e.printStackTrace();
            return new PrintResult(PrintStatus.FAILED, "Failed to load PDF: " + e.getMessage(), e);
        }
    }
    
    // Simple print without monitoring (fastest)
    public static PrintResult printSimple(byte[] pdfBytes, String printerName) {
        System.out.println("Starting simple print (no monitoring)...");
        
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PrinterJob job = PrinterJob.getPrinterJob();

            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            PrintService targetPrinter = null;
            for (PrintService ps : services) {
                if (ps.getName().equalsIgnoreCase(printerName)) {
                    targetPrinter = ps;
                    break;
                }
            }
            if (targetPrinter == null) {
                return new PrintResult(PrintStatus.FAILED, "Printer not found: " + printerName);
            }
            
            job.setPrintService(targetPrinter);
            job.setPrintable(new PDFPrintable(document, Scaling.ACTUAL_SIZE));
            
            try {
                job.print();
                System.out.println("Print command sent successfully");
                return new PrintResult(PrintStatus.SUCCESS, "Print command sent (no monitoring)");
            } catch (Exception e) {
                return new PrintResult(PrintStatus.FAILED, "Print failed: " + e.getMessage(), e);
            }
            
        } catch (Exception e) {
            return new PrintResult(PrintStatus.FAILED, "Failed to load PDF: " + e.getMessage(), e);
        }
    }
}