@echo off

cd /d "C:\Users\Lenovo\Desktop\java\data-pipeline-project"

echo. >> pipeline.log
echo ===== Pipeline Started: %date% %time% ===== >> pipeline.log

call mvn -q compile exec:java >> pipeline.log 2>&1

if errorlevel 1 (
    echo ===== PIPELINE FAILED: %date% %time% ===== >> pipeline.log
    exit /b 1
)

echo ===== PIPELINE SUCCESS: %date% %time% ===== >> pipeline.log

exit /b 0
