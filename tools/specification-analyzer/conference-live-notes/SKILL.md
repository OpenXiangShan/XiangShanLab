---
name: conference-live-notes
description: >
  Real-time conference/event note-taking and documentation workflow. Activate when the user
  says they are attending a conference, seminar, workshop, or industry event, and will be
  sending photos (slides, booth displays), voice notes, and text observations for the agent
  to organize into structured meeting notes. The agent acts as a remote note-taking assistant,
  processing incoming materials in real-time during the event. Also use when the user asks to
  set up or review this workflow before an event.
---

# Conference Live Notes

Real-time conference documentation workflow. The user attends an event, sends photos/voice/text
observations; the agent processes everything into structured, searchable documentation.

## Pre-Event Setup

When the user announces they will attend an event:

1. **Confirm event details** (ask if not provided):
   - Event name, date, location
   - Host/organizer
   - Topics/themes
   - Any preparation materials (agenda, speaker list)
2. **Create directory structure:**
   ```
   ~/.openclaw/workspace/meetings/YYYY-MM-DD-<event-slug>/
   ├── photos/          # All incoming photos saved here
   └── (other files added during the event)
   ```
3. **Update memory/fragments/YYYY-MM-DD.md** with a header for this event.

## During the Event

### Processing Photos (Slides + Booth)

For each photo the user sends:

1. **Save** to `photos/` with sequential naming:
   - Slide photos: `slide-NN-<descriptive-slug>.jpg` (NN = 01, 02, 03...)
   - Booth/exhibit photos: `booth-NN-<descriptive-slug>.jpg`
   - Other photos: `misc-NN-<descriptive-slug>.jpg`
2. **OCR analyze** the photo using the `image` tool - extract all visible text, diagrams, data
3. **Append entry** to `photos/README.md`:
   ```markdown
   ### slide-NN-<slug>.jpg
   - **Content:** Brief description of the slide content
   - **Key points (OCR):**
     - Key point 1
     - Key point 2
     - ...
   ```
4. **Briefly acknowledge** to the user (short confirmation, don't flood with long replies)

### Processing Voice/Text Observations

When the user sends voice messages or text notes:

1. **Transcribe** voice if needed (use Whisper or read transcription)
2. **Extract insights** - what caught the user's attention, new concepts, opinions
3. **Append** to `<user>-insights.md`:
   ```markdown
   ### Talk N: <Speaker/Topic>
   - Key observations from the user
   - 💡 New concepts worth noting
   - Personal reactions and analysis
   ```
4. **Respond briefly** - acknowledge, maybe ask a clarifying question if something is unclear

### Grouping by Talk/Session

As photos come in, group them by speaker/session. Update `photos/README.md` section headers:

```markdown
## Talk 1: Frank Lin (Andes Chairman)

### slide-01-xxx.jpg
...
### slide-02-xxx.jpg
...
```

## Post-Event Processing

After the event (user signals completion or sends final batch):

1. **Write `notes.md`** - Comprehensive structured notes:
   - Market/trend analysis
   - Company/product summaries
   - Technology highlights
   - Ecosystem/supply chain observations
   - Actionable insights for the user's work

2. **Finalize `photos/README.md`** - Complete index with:
   - Event metadata header (name, date, host, sponsors)
   - All slides organized by session/speaker
   - All booth/exhibit photos with descriptions

3. **Update `memory/fragments/YYYY-MM-DD.md`** with:
   - Event summary
   - Key takeaways (bullet list)
   - Reference to the meetings directory

4. **Check for supplementary materials** - If the user obtained PPTs/PDFs from exhibitors:
   - Save to a subdirectory (e.g., `ppt-from-<vendor>/`)
   - Note in README.md

## File Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Slide photo | `slide-NN-<slug>.jpg` | `slide-03-andes-product-lineup.jpg` |
| Booth photo | `booth-NN-<slug>.jpg` | `booth-01-andes-demo.jpg` |
| Large/clear photo | `slide-NN-<slug>.png` | `slide-29-large.png` |
| Vendor PPT | `ppt-from-<vendor>/` | `ppt-from-osyx/` |
| User insights | `<name>-insights.md` | `guodong-insights.md` |
| Structured notes | `notes.md` | Industry notes, white-paper material |
| Photo index | `photos/README.md` | Full OCR index of all photos |

## Key Principles

- **Low latency during event** - Quick saves and brief acks; heavy processing after
- **OCR everything** - Every photo gets full text extraction
- **Preserve user voice** - Insights file captures the user's own observations, not AI summaries
- **Structured output** - notes.md is clean enough to reuse for whitepapers/reports
- **Sequential numbering** - Never reorder; sequence = timeline of the event
- **Bilingual ready** - Technical terms keep original language; descriptions adapt to user's language
