#!/bin/bash

# Create placeholder launcher icons for all densities

# Base64 encoded 1x1 pixel transparent PNG
TRANSPARENT_PNG="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="

# Base64 encoded 1x1 pixel colored PNG (blue) for round icon
BLUE_PNG="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg=="

DENSITIES=("mdpi" "hdpi" "xhdpi" "xxhdpi" "xxxhdpi")

for density in "${DENSITIES[@]}"; do
    dir="app/src/main/res/mipmap-$density"
    mkdir -p "$dir"
    
    echo "$TRANSPARENT_PNG" | base64 -d > "$dir/ic_launcher.png"
    echo "$BLUE_PNG" | base64 -d > "$dir/ic_launcher_round.png"
    
    echo "Created $dir/ic_launcher.png and ic_launcher_round.png"
done

echo "✅ Placeholder icons generated."