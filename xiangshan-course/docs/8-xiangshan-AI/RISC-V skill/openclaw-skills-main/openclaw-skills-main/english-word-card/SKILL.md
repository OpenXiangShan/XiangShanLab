---
name: english-word-card
description: Generate illustrated English word learning cards for kids. Activate when the user asks to explain an English word to their daughter/child, or requests a vocabulary card with image and explanation.
---

# English Word Card

Generate a kid-friendly illustrated vocabulary card with text explanation.

## Workflow (optimized ~65s)

1. **Reuse existing Gemini tab** or navigate to `https://gemini.google.com/app`:
   - If a Gemini tab is already open, use `browser action=navigate` on that tab
   - Only `browser action=open` if no tab exists
   - Skip unnecessary snapshot/click - type directly using the input ref (typically `e14` on fresh page, `e18`/`e19` on existing conversation)

2. **Type prompt and submit** (one step, no separate click):
   ```
   browser action=act, kind=type, ref=<input_ref>,
   text="Generate an image for a child to learn the English word '[WORD]'. Draw a cute cartoon illustration that shows the meaning of '[WORD]'. Use bright colors, kid-friendly style. Write the word '[WORD]' in big friendly colorful letters at the top.",
   submit=true
   ```

3. **Wait ~15s** for image generation (not 18s - 15s is enough)

4. **Snapshot** to find "Copy image" button ref

5. **Click "Copy image"** button

6. **Extract PNG from macOS clipboard** (sleep 1s then extract):
   ```
   sleep 1 && python3 -c "
   import subprocess
   p = subprocess.run(['osascript', '-e', 'the clipboard as «class PNGf»'], capture_output=True)
   text = p.stdout.decode('utf-8', errors='replace')
   hex_str = text.split('«data PNGf')[1].split('»')[0]
   binary = bytes.fromhex(hex_str)
   with open('/Users/christopherxu/.openclaw/workspace/word-card.png', 'wb') as f:
       f.write(binary)
   print(f'Saved {len(binary)} bytes')
   "
   ```
   This gives the original 1024x1024 PNG (~1.3-1.6MB)

7. **Send image + text** via `message action=send` with `media` + `caption`

## Text Format

```
🐴 **WORD** /pronunciation/

**Meaning:** English definition in simple terms

**Example sentences:**
• Sentence 1
• Sentence 2

💡 **Extra:** Any extended/colloquial usage

---

**中文解释：** Chinese explanation
**例句翻译：** Chinese translation of examples
```

## Optimization Tips

- **Reuse tabs** - don't open new tabs each time, navigate existing ones
- **Skip redundant snapshots** - after page load, type directly if you know the ref
- **Combine exec steps** - sleep + python extraction in one exec call
- **15s generation wait** is sufficient (tested), not 18-20s
- Each tool call has ~1-2s overhead, minimize total number of calls

## Fallback

If "Copy image" + clipboard fails, fall back to element screenshot:
- `browser action=screenshot` with `selector="img[alt*='AI generated']"`
- Then crop with `sips` to remove UI chrome

## Notes

- Keep language simple - target audience is a young child (Emily 11yr, Catherine 8yr)
- Illustrations should be cute, colorful, and clearly depict the word's meaning
- If Gemini web UI is unavailable, fall back to nano-banana-pro skill (API)
- Always send image and text together in one message
- Download button does NOT work (Blob URL issue in managed Chrome profile)
