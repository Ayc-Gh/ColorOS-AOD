#!/system/bin/sh

OUT_DIR="/storage/emulated/0/Documents/ColorOS-AOD/log"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_FILE="$OUT_DIR/aod-trace-$STAMP.log"

mkdir -p "$OUT_DIR" || exit 1
logcat -c

{
  echo "ColorOS AOD native state-machine trace"
  echo "Started: $(date)"
  echo "Output: $OUT_FILE"
  echo "Android: $(getprop ro.build.version.release) SDK=$(getprop ro.build.version.sdk)"
  echo "Build: $(getprop ro.build.display.id)"
  echo "--- initial power snapshot ---"
  dumpsys power 2>/dev/null | grep -E 'mWakefulness|mIsPowered|mPlugType|Display Power' | head -40
  echo "--- trace begins ---"
} | tee "$OUT_FILE"

echo "Press Ctrl+C after AOD naturally turns off."

exec logcat -v threadtime \
  AOD_Enhance:* \
  DozeMachine:* \
  DozeService:* \
  DozeScreenState:* \
  AODDisplayUtil:* \
  BaseDisplayUtil:* \
  OplusDozeServiceExImpl:* \
  '*:S' >> "$OUT_FILE"
