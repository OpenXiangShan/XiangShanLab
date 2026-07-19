# VS Code Waveform Display

Use this reference when the generated analysis contains fenced `waveform-draw` blocks and the user needs the signals rendered in VS Code.

## Extension

Install the VS Code extension:

```bash
code --install-extension bmpenuelas.markdown-preview-wavedrom
```

The extension adds WaveDrom rendering to the built-in Markdown Preview. It expects a configurable Markdown fence language identifier; this skill uses `waveform-draw` for compatibility with existing documents.

## Workspace Setting

Add this setting to the workspace that contains the course Markdown files:

```json
{
  "markdown-preview-wavedrom.LanguageIdentifier": "waveform-draw"
}
```

The course repository keeps this in `.vscode/settings.json`. Do not change the fence to an arbitrary language name without changing this setting as well.

## Authoring Contract

- Start each timing block with a fenced block whose info string is `waveform-draw`.
- Put strict JSON inside the block; the root object must contain a `signal` array.
- Put `clk` first and use WaveDrom wave strings such as `0`, `1`, `.`, `x`, and `=`.
- Keep payload labels in `data`; do not embed source links or prose in signal names.
- Use real Chisel signal names for `valid`, `ready`, `fire`, `flush`, `redirect`, `cancel`, `miss`, and response signals.
- Show one normal transfer and one stall, cancel, replay, or exception case when those behaviors exist.

## Preview and Troubleshooting

1. Open the `.md` file in VS Code.
2. Run `Markdown: Open Preview to the Side`.
3. If the block remains text, verify the extension is installed in the same local/SSH/Dev Container authority as the Markdown file.
4. Verify the setting key and value exactly match `markdown-preview-wavedrom.LanguageIdentifier` and `waveform-draw`.
5. Validate the block as JSON; comments and trailing commas prevent rendering.

The source editor is expected to show the fenced JSON as text. Rendering occurs in the Markdown Preview pane.
