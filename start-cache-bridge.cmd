@echo off
chcp 65001 >nul
python "%~dp0cache-bridge.py" --host 0.0.0.0 --port 8765
pause
