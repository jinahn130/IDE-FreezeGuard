#!/bin/bash

# VS Code Extension Debug Launcher
# This script ensures the correct workspace is opened for debugging

echo "🚀 Launching VS Code Extension Development..."

# Navigate to the extension directory
cd "$(dirname "$0")"

# Compile the extension first
echo "📦 Compiling TypeScript..."
npm run compile

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
    echo "🔧 Opening VS Code workspace..."
    
    # Open the workspace file in VS Code
    code vscode-freezeguard.code-workspace
    
    echo ""
    echo "📝 Next steps:"
    echo "1. Wait for VS Code to fully load"
    echo "2. Press F5 or go to Run & Debug panel"
    echo "3. Select 'Run Extension' and click play button"
    echo "4. A new VS Code window will open with your extension loaded"
    echo "5. In the new window, use Cmd+Shift+P and search for 'Freeze Guard'"
else
    echo "❌ Compilation failed. Please fix TypeScript errors first."
    exit 1
fi