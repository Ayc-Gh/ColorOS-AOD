#!/system/bin/sh

OUT_DIR="/storage/emulated/0/Documents/ColorOS-AOD/log"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="$OUT_DIR/aod-detailed-$STAMP.log"

mkdir -p "$OUT_DIR" || exit 1
echo "ColorOS AOD detailed log capture" | tee "$OUT_FILE"
echo "Started: $(date)" | tee -a "$OUT_FILE"
echo "Output: $OUT_FILE" | tee -a "$OUT_FILE"
echo "Press Ctrl+C to stop." | tee -a "$OUT_FILE"

exec logcat -v threadtime -s AOD_Enhance:* >> "$OUT_FILE"
