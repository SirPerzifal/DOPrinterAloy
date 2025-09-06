import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class PaperSizeComboBox extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    // PaperSize class to hold name + dimensions
    public static class PaperSize {
        private final String name;
        private final String dimensions;

        public PaperSize(String name, String dimensions) {
            this.name = name;
            this.dimensions = dimensions;
        }

        @Override
        public String toString() {
            return name + " (" + dimensions + ")";
        }
    }

    @Override
    public void start(Stage primaryStage) {
        ComboBox<PaperSize> paperCombo = new ComboBox<>();

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

            // ISO C Series (Envelopes)
            new PaperSize("C0", "917 x 1297 mm"),
            new PaperSize("C1", "648 x 917 mm"),
            new PaperSize("C2", "458 x 648 mm"),
            new PaperSize("C3", "324 x 458 mm"),
            new PaperSize("C4", "229 x 324 mm"),
            new PaperSize("C5", "162 x 229 mm"),
            new PaperSize("C6", "114 x 162 mm"),
            new PaperSize("C7", "81 x 114 mm"),
            new PaperSize("C8", "57 x 81 mm"),
            new PaperSize("C9", "40 x 57 mm"),
            new PaperSize("C10", "28 x 40 mm"),

            // North American Sizes
            new PaperSize("Letter", "8.5 x 11 in"),
            new PaperSize("Legal", "8.5 x 14 in"),
            new PaperSize("Tabloid", "11 x 17 in"),
            new PaperSize("Ledger", "17 x 11 in"),
            new PaperSize("Executive", "7.25 x 10.5 in"),
            new PaperSize("Statement", "5.5 x 8.5 in"),

            // Architectural Sizes
            new PaperSize("ARCH A", "9 x 12 in"),
            new PaperSize("ARCH B", "12 x 18 in"),
            new PaperSize("ARCH C", "18 x 24 in"),
            new PaperSize("ARCH D", "24 x 36 in"),
            new PaperSize("ARCH E", "36 x 48 in"),

            // Common Photo Sizes
            new PaperSize("4R", "4 x 6 in"),
            new PaperSize("5R", "5 x 7 in"),
            new PaperSize("8R", "8 x 10 in"),
            new PaperSize("10R", "10 x 12 in")
        );

        // Default selection (optional)
        paperCombo.getSelectionModel().select(new PaperSize("A4", "210 x 297 mm"));

        VBox root = new VBox(10, paperCombo);
        root.setStyle("-fx-padding: 20px;");

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setTitle("Paper Size Selector");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
