#!/bin/bash
# Deploy clock face to Samsung Gear 1
# Usage: ./deploy.sh [settings.json]
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
SDB="$HOME/tizen-studio/tools/sdb"
TIZEN="$HOME/tizen-studio/tools/ide/bin/tizen"
SETTINGS="${1:-$DIR/settings.json}"

echo "=== Gear Clock Face Deploy ==="
echo "Settings: $SETTINGS"

# Check SDB connection
if ! $SDB devices | grep -q "SM-V700"; then
    echo "ERROR: Watch not connected via USB. Connect the charging cradle."
    exit 1
fi

# Read settings and inject into index.html
SETTINGS_JS=$(python3 -c "
import json
with open('$SETTINGS') as f:
    s = json.load(f)
print('window.CLOCK_SETTINGS=' + json.dumps(s) + ';')
")

# Build index.html with injected settings
cat > "$DIR/build/index.html" << HTMLEOF
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <link rel="stylesheet" href="style.css">
    <script>$SETTINGS_JS</script>
</head>
<body>
    <div id="clock">
        <div id="date-line"></div>
        <div id="time-line">
            <span id="hours"></span><span id="colon">:</span><span id="minutes"></span>
        </div>
        <div id="seconds-line">
            <span id="seconds"></span>
        </div>
        <div id="info-line">
            <span id="battery"></span>
            <span id="day-name"></span>
        </div>
    </div>
    <div id="sap-status"></div>
    <script src="clock.js"></script>
</body>
</html>
HTMLEOF

# Copy other files to build dir
cp "$DIR/config.xml" "$DIR/style.css" "$DIR/clock.js" "$DIR/icon.png" "$DIR/build/"

# Copy SAP accessoryservices.xml
mkdir -p "$DIR/build/res/xml"
cp "$DIR/res/xml/accessoryservices.xml" "$DIR/build/res/xml/"

# Package
cd "$DIR/build"
rm -f Organizeur.wgt
$TIZEN package -t wgt -s OrganizeurProfile -- . 2>&1 | grep -E "Package|Error"

# Install
echo "Installing on watch..."
$SDB install Organizeur.wgt 2>&1 | tail -3

echo "=== Done ==="
