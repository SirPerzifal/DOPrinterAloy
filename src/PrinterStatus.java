import java.io.BufferedReader;
import java.io.InputStreamReader;

public class PrinterStatus {
    public static void checkPrinterJobs(String printerName) throws Exception {
        Process p = Runtime.getRuntime().exec(
            "wmic printjob get Name,JobId,Document,Status,TotalPages,PagesPrinted"
        );

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(printerName)) {
                    System.out.println("JOB INFO: " + line);
                }
            }
        }
    }
}
