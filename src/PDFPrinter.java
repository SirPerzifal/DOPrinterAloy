import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;
import com.profesorfalken.wmi4java.WMI4Java;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.PrinterState;
import javax.print.attribute.standard.PrinterStateReasons;
import javax.print.event.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;

public class PDFPrinter {
    public static void printAndWait(byte[] pdfBytes, String printerName) throws Exception {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PrinterJob job = PrinterJob.getPrinterJob();

            // Cari printer
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            PrintService targetPrinter = null;
            for (PrintService ps : services) {
                if (ps.getName().equalsIgnoreCase(printerName)) {
                    targetPrinter = ps;
                    break;
                }
            }
            if (targetPrinter == null) {
                throw new RuntimeException("Printer not found: " + printerName);
            }
            job.setPrintService(targetPrinter);

            // Render halaman PDF → Printable
            job.setPrintable(new PDFPrintable(document, Scaling.SHRINK_TO_FIT));

            // Ini sinkron, akan block sampai selesai print
            job.print();
            int maxWaitMs = 60000; // 60 detik max
            int intervalMs = 2000;
            int waited = 0;

            while (waited < maxWaitMs) {
                PrintServiceAttributeSet attrs = targetPrinter.getAttributes();

                PrinterState state = (PrinterState) attrs.get(PrinterState.class);
                PrinterStateReasons reasons = (PrinterStateReasons) attrs.get(PrinterStateReasons.class);

                System.out.println("Printer state: " + state);
                if (reasons != null && !reasons.isEmpty()) {
                    System.out.println("Reasons: " + reasons);
                }

                // cek complete / error / out of paper
                if (state == PrinterState.IDLE) {
                    System.out.println("✅ Print job done.");
                    break;
                } else if (state == PrinterState.STOPPED) {
                    System.out.println("❌ Print job failed / printer stopped.");
                    break;
                }

                Thread.sleep(intervalMs);
                waited += intervalMs;
            }

            if (waited >= maxWaitMs) {
                System.out.println("⚠️ Timeout waiting for print completion.");
            }
        }
        
    }


    public static void printAndMonitor(byte[] pdfBytes, String printerName, String jobName) throws Exception {
        // 1. Kirim PDF ke printer
        // Cari printer sesuai nama
        PrintService foundService = null;
        for (PrintService ps : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (ps.getName().equalsIgnoreCase(printerName)) {
                foundService = ps;
                break;
            }
        }
        final PrintService service = foundService;

        if (service == null) {
            throw new RuntimeException("Printer not found: " + printerName);
        }

        // Bikin DocPrintJob
        DocPrintJob job = service.createPrintJob();

        // Tambahin listener
        job.addPrintJobListener(new PrintJobAdapter() {
            @Override
            public void printJobCompleted(PrintJobEvent pje) {
                System.out.println("✅ Print job completed on printer: " + service.getName());
            }

            @Override
            public void printJobFailed(PrintJobEvent pje) {
                System.out.println("❌ Print job failed!");
            }

            @Override
            public void printJobCanceled(PrintJobEvent pje) {
                System.out.println("⚠️ Print job canceled!");
            }

            @Override
            public void printJobNoMoreEvents(PrintJobEvent pje) {
                System.out.println("ℹ️ No more print events.");
            }
        });
        File tempFile = File.createTempFile("printjob_", ".pdf");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(pdfBytes);
        }

        try (FileInputStream fis = new FileInputStream(tempFile)) {
            Doc doc = new SimpleDoc(fis, DocFlavor.INPUT_STREAM.AUTOSENSE, null);

            PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
            pras.add(new JobName(jobName, null));

            System.out.println("➡️ Sending job to printer: " + service.getName());
            job.print(doc, pras);
        }

            // monitorWindowsPrintJob(printerName);
    }

    public static void monitorWindowsPrintJob(String printerName) {
        try {
            // Property yang kita mau ambil dari Print Job
            List<String> props = Arrays.asList(
                "JobId",
                "Document",
                "Status",
                "Owner",
                "PagesPrinted"
            );

            // Query ke WMI untuk PrintJob
            String result = WMI4Java.get()
                    .VBSEngine() // bisa diganti .COM() kalau COM engine support
                    .properties(props)
                    .getRawWMIObjectOutput("Win32_PrintJob");

            System.out.println("=== Print Job Status ===");
            System.out.println(result);

            // Kalau mau filtering job tertentu
            if (result != null && result.contains(printerName)) {
                System.out.println("Job ditemukan untuk printer: " + printerName);
            } else {
                System.out.println("Belum ada job aktif untuk printer: " + printerName);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
