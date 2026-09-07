#!/system/bin/sh
# Runs ON the device, as root, detached. Owns the virtual pad for its whole lifetime:
# when this script dies the uinput device disappears, so it must stay in the foreground.
FIFO=/data/local/tmp/xboxpad.fifo
REG=/data/local/tmp/xboxpad_register.json

rm -f "$FIFO"
mknod "$FIFO" p
chmod 666 "$FIFO"

# A writer that never closes, so the reader below never sees EOF and tears the pad down.
sleep 86400 > "$FIFO" &
echo $! > /data/local/tmp/xboxpad_holder.pid

# Hand the descriptor over once the reader is listening.
( sleep 1; cat "$REG" > "$FIFO" ) &

# Foreground: this process IS the device.
exec uinput - < "$FIFO"
