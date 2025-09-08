@echo off
echo Launching AloyDesktop...

REM Try with JavaFX 17 first
java --module-path lib\javafx-sdk-17.0.16\lib --add-modules javafx.controls,javafx.fxml,javafx.graphics -jar DOPrinterAloy.jar

REM If that fails, try with software rendering
if %ERRORLEVEL% neq 0 (
    echo Retrying with software rendering...
    java --module-path lib\javafx-sdk-17.0.16\lib --add-modules javafx.controls,javafx.fxml,javafx.graphics -Dprism.order=sw -jar DOPrinterAloy.jar
)

REM If that fails too, try JavaFX 21
if %ERRORLEVEL% neq 0 (
    echo Retrying with JavaFX 21...
    java --module-path lib\javafx-sdk-21.0.8\lib --add-modules javafx.controls,javafx.fxml,javafx.graphics -jar DOPrinterAloy.jar
)

echo.
echo Press any key to exit...
pause > nul