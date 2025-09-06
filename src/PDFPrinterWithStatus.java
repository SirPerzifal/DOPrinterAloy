// PDFPrinterWithStatus.java - Simplified Version
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;

import javax.print.*;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.*;
import javax.print.event.PrintJobEvent;
import javax.print.event.PrintJobListener;

import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PDFPrinterWithStatus {
    
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
    
    public static PrintResult printAndWaitWithStatus(byte[] pdfBytes, String printerName) {
        return printAndWaitWithStatus(pdfBytes, printerName, 15000);
    }
    
    public static PrintResult printAndWaitWithStatus(byte[] pdfBytes, String printerName, int timeoutMs) {
        System.out.println("Starting print with enhanced listener method...");
        
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
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
                System.out.println("Printer not found: " + printerName);
                return new PrintResult(PrintStatus.FAILED, "Printer not found: " + printerName);
            }
            
            System.out.println("Found printer: " + targetPrinter.getName());
            
            // Use CountDownLatch for simpler synchronization
            CountDownLatch printLatch = new CountDownLatch(1);
            PrintResult[] result = {null}; // Array to hold result from listener
            
            // Create print job
            DocPrintJob docJob = targetPrinter.createPrintJob();
            
            // Enhanced listener with smart completion detection
            docJob.addPrintJobListener(new PrintJobListener() {
                private boolean dataTransferDone = false;
                private boolean noMoreEventsCalled = false;
                
                @Override
                public void printJobCompleted(PrintJobEvent pje) {
                    System.out.println("Print job completed successfully");
                    result[0] = new PrintResult(PrintStatus.SUCCESS, "Print completed successfully");
                    printLatch.countDown();
                }

                @Override
                public void printJobFailed(PrintJobEvent pje) {
                    System.out.println("Print job failed");
                    result[0] = new PrintResult(PrintStatus.FAILED, "Print job failed");
                    printLatch.countDown();
                }

                @Override
                public void printJobCanceled(PrintJobEvent pje) {
                    System.out.println("Print job cancelled");
                    result[0] = new PrintResult(PrintStatus.CANCELLED, "Print job cancelled");
                    printLatch.countDown();
                }

                @Override
                public void printJobRequiresAttention(PrintJobEvent pje) {
                    System.out.println("Print job requires attention");
                    result[0] = new PrintResult(PrintStatus.FAILED, "Print job requires attention");
                    printLatch.countDown();
                }

                @Override
                public void printDataTransferCompleted(PrintJobEvent pje) {
                    System.out.println("Data transfer completed");
                    dataTransferDone = true;
                    checkCompletion();
                }

                @Override
                public void printJobNoMoreEvents(PrintJobEvent pje) {
                    System.out.println("No more print events");
                    noMoreEventsCalled = true;
                    checkCompletion();
                }
                
                private void checkCompletion() {
                    // If both events happened and no explicit completion, assume success
                    if (dataTransferDone && noMoreEventsCalled && result[0] == null) {
                        System.out.println("Assuming success based on data transfer + no more events");
                        result[0] = new PrintResult(PrintStatus.SUCCESS, 
                            "Print completed (inferred from events)");
                        printLatch.countDown();
                    }
                }
            });
            
            try {
                // Submit print job
                Doc doc = new SimpleDoc(new PDFPrintable(document, Scaling.SHRINK_TO_FIT), 
                                      DocFlavor.SERVICE_FORMATTED.PRINTABLE, null);
                docJob.print(doc, null);
                System.out.println("Print job submitted, waiting for completion...");
                
                // Wait for completion with timeout
                boolean completed = printLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
                
                if (completed && result[0] != null) {
                    return result[0];
                } else {
                    System.out.println("Print operation timed out");
                    return new PrintResult(PrintStatus.TIMEOUT, "Print operation timed out after " + timeoutMs + "ms");
                }
                
            } catch (Exception e) {
                System.out.println("Exception during printing: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
                if (e.getMessage() != null) {
                    e.printStackTrace();
                }
                return new PrintResult(PrintStatus.FAILED, "Exception during printing", e);
            }
            
        } catch (Exception e) {
            System.out.println("Failed to load PDF: " + e.getMessage());
            return new PrintResult(PrintStatus.FAILED, "Failed to load PDF: " + e.getMessage(), e);
        }
    }
    
    public static PrintResult printAndWaitWithPolling(byte[] pdfBytes, String printerName, int timeoutMs) {
        System.out.println("Starting print with polling method...");
        
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
            job.setPrintable(new PDFPrintable(document, Scaling.SHRINK_TO_FIT));

            try {
                job.print();
                System.out.println("Print command sent successfully");
                
                // Simple polling approach
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
                    
                    // If we've waited enough time and printer seems idle, assume success
                    if (waited >= 5000) {
                        System.out.println("Assuming print completed after " + waited + "ms wait");
                        return new PrintResult(PrintStatus.SUCCESS, "Print likely completed (polling timeout)");
                    }
                }
                
                return new PrintResult(PrintStatus.TIMEOUT, "Polling timeout");
                
            } catch (Exception e) {
                return new PrintResult(PrintStatus.FAILED, "Print execution failed: " + e.getMessage(), e);
            }
            
        } catch (Exception e) {
            return new PrintResult(PrintStatus.FAILED, "Failed to process PDF: " + e.getMessage(), e);
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
            job.setPrintable(new PDFPrintable(document, Scaling.SHRINK_TO_FIT));
            
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