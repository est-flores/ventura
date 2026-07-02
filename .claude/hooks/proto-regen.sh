#!/bin/bash
# proto-regen.sh
#
# Automatically runs buf generate when a .proto file is modified.
# Configured as a PostToolUse hook in .claude/settings.json.
#
# Claude Code exposes the modified file path via the CLAUDE_TOOL_INPUT_PATH
# environment variable. If that variable is unavailable in your version,
# run `/hooks` in Claude Code to configure this interactively instead.

set -e

FILE="${CLAUDE_TOOL_INPUT_PATH:-}"

# If no path exposed, fall back to checking if any proto file is newer
# than the last generated output
if [ -z "$FILE" ]; then
    if find proto/ -name "*.proto" -newer gen/go/ventura/feed/v1/feed.pb.go 2>/dev/null | grep -q .; then
        echo "[hook] Proto file modified — running buf generate..."
        buf generate && echo "[hook] buf generate: done ✓" || echo "[hook] buf generate: FAILED ✗"
    fi
    exit 0
fi

# If path is exposed, only regenerate if it's a .proto file
if [[ "$FILE" == *.proto ]]; then
    echo "[hook] Proto file modified: $FILE — running buf generate..."
    buf generate && echo "[hook] buf generate: done ✓" || echo "[hook] buf generate: FAILED ✗"
fi