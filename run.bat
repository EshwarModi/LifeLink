@echo off
REM ============================================
REM LifeLink - Blood Donation Platform
REM Automated Setup and Run Script for Windows
REM ============================================

echo.
echo ╔════════════════════════════════════════════╗
echo ║        LifeLink - Starting Application     ║
echo ╚════════════════════════════════════════════╝
echo.

REM Check if Python is installed
python --version >nul 2>&1
if errorlevel 1 (
    echo ❌ ERROR: Python is not installed or not in PATH
    echo Please install Python from https://www.python.org/downloads/
    echo Make sure to check "Add Python to PATH" during installation
    pause
    exit /b 1
)

echo ✓ Python found
echo.

REM Check if virtual environment exists
if not exist "venv" (
    echo 📦 Creating virtual environment...
    python -m venv venv
    if errorlevel 1 (
        echo ❌ ERROR: Failed to create virtual environment
        pause
        exit /b 1
    )
    echo ✓ Virtual environment created
) else (
    echo ✓ Virtual environment already exists
)

echo.
echo 🔧 Activating virtual environment...
call venv\Scripts\activate.bat
if errorlevel 1 (
    echo ❌ ERROR: Failed to activate virtual environment
    pause
    exit /b 1
)
echo ✓ Virtual environment activated

echo.
echo 📥 Installing dependencies from requirements.txt...
pip install --upgrade pip
python -m pip install -r requirements.txt
if errorlevel 1 (
    echo ❌ ERROR: Failed to install dependencies
    echo Please check your internet connection and try again
    pause
    exit /b 1
)
echo ✓ Dependencies installed successfully

echo.
echo ╔════════════════════════════════════════════╗
echo ║      Starting LifeLink Application         ║
echo ╚════════════════════════════════════════════╝
echo.
echo 🌐 Server will be available at:
echo    http://localhost:5000
echo.
echo 📝 Press Ctrl+C to stop the server
echo.

REM Run the application
python app.py

REM If app exits, show a message
echo.
echo Application has stopped.
pause
