import javax.print.PrintService;
import javax.print.PrintServiceLookup;

public class PrinterUtils {
    public static PrintService[] getPrinters() {
        return PrintServiceLookup.lookupPrintServices(null, null);
    }
}