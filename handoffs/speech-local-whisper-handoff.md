# speech — Local Whisper Transcription Handoff

## Purpose

Replace the legacy Windows `System.Speech` (SAPI) recognition path in the
`speech` project with local, GPU-accelerated transcription via whisper.cpp.
SAPI accuracy was poor ("most words not understood"). whisper.cpp on the
user's RTX 4060 Ti has already been validated standalone and produces
fast, accurate, word-perfect transcription.

## Already Done (do not redo)

- `whisper-cublas-11.8.0-bin-x64.zip` (build b4938) downloaded and
  extracted to `speech/Release/`. Contains `whisper-cli.exe`,
  `whisper-server.exe`, `whisper.dll`, `ggml.dll`, `ggml-cuda.dll`, and
  bundled CUDA 11.8 runtime DLLs. No CUDA toolkit install required on
  the target machine — the runtime is bundled.
- Model file `ggml-large-v3-q5_0.bin` (1.1 GiB, full 32-layer decoder,
  q5_0 quantization) downloaded to the `speech/` repo root.
- NVIDIA driver updated to 610.88 (CUDA 13.3 capable, backward
  compatible with the bundled CUDA 11.8 runtime).
- Standalone CLI test passed:
  ```
  Release/whisper-cli.exe -m ggml-large-v3-q5_0.bin -f jfk.wav --no-timestamps
  ```
  Confirmed CUDA0 backend active (not CPU fallback), model loads at
  ~1080 MB VRAM, total processing time ~1.3 sec for an 11-second clip,
  transcription output was word-perfect.

## Model Choice Rationale (for context, do not re-litigate)

`large-v3-q5_0` was chosen over `large-v3-turbo` variants because the
user explicitly prioritized transcription accuracy over speed/VRAM
headroom for this phase. `large-v3` keeps the full 32-layer decoder;
`turbo` variants prune it to 4 layers for speed at some accuracy cost.
If VRAM contention with other GPU workloads (e.g. LM Studio) becomes a
problem later, `large-v3-turbo-q8_0` is the documented fallback.

## Current Architecture (relevant classes)

- `SpeechInput` — interface, `AudioInput capture(Duration maximumDuration)`.
- `SpeechToText` — interface, `String transcribe(AudioInput audioInput)`.
- `AudioInput` — record, currently `(Duration maximumDuration, String recognizedText)`.
- `WindowsSpeechInput` — implements `SpeechInput`. Runs a PowerShell/SAPI
  script via `WindowsSpeechProcess.run(...)` that captures audio **and**
  recognizes it in one step, then stuffs the recognized text directly
  into `AudioInput.recognizedText`.
- `WindowsSpeechToText` — implements `SpeechToText`. Trivial pass-through:
  `return audioInput.recognizedText();`. Recognition already happened
  during capture.
- `WindowsSpeechProcess` — shells out to `powershell.exe`, captures
  stdout, handles timeout/cancellation. This is the pattern to copy for
  invoking `whisper-cli.exe`.
- `SpeechApplication.startSpeechLoop()` already treats capture and
  transcribe as two distinct pipeline stages (`LISTENING` then
  `TRANSCRIBING` states) — the UI/state machine does not need to change.

## The Real Design Problem

whisper.cpp needs capture and recognition to be genuinely separate
steps: record raw 16kHz mono 16-bit PCM audio to a `.wav` file, then
run `whisper-cli.exe` against that file. The current `AudioInput`
record has no field for raw audio — it assumes recognition already
happened by the time `AudioInput` exists.

`AudioInput` must change shape. Recommended: replace `recognizedText`
with a `Path audioFilePath` (or similar), e.g.:

```java
public record AudioInput(Duration maximumDuration, Path audioFilePath) {
}
```

This is a breaking change to the `SpeechInput`/`SpeechToText` contract.
Both `WindowsSpeechInput`/`WindowsSpeechToText` (SAPI path) and any new
local-whisper classes must be updated to the new shape. Decide whether
to keep the SAPI path alive as a fallback/comparison option or remove
it outright — recommend keeping it behind the same interface for now,
since it's a working reference implementation to diff against.

## Required Work

1. **Capture raw audio to WAV on Windows.** `WindowsSpeechInput` needs
   a PowerShell/.NET script (or a different approach) that records
   from the default microphone to a 16kHz mono 16-bit PCM `.wav` file
   for up to `maximumDuration`, rather than performing SAPI recognition
   inline. This is new work — nothing in the current codebase does raw
   audio capture without simultaneous recognition. Investigate
   `System.Media.SoundRecorder`, NAudio (would add a .NET dependency,
   probably avoid), or `System.Speech`'s audio stream sink redirected
   to a WAV writer instead of a recognizer.
2. **New `LocalWhisperSpeechToText implements SpeechToText`.** Given an
   `AudioInput` with a WAV file path, shell out to
   `Release/whisper-cli.exe -m ggml-large-v3-q5_0.bin -f <path>
   --no-timestamps` using the same `ProcessBuilder`/timeout/cancel
   pattern as `WindowsSpeechProcess`, capture stdout, return it as the
   transcript. Consider extracting a `WhisperCliProcess` helper class
   mirroring `WindowsSpeechProcess` rather than duplicating the
   process-management logic.
3. **Model and binary path configuration.** Do not hardcode
   `Release/whisper-cli.exe` and `ggml-large-v3-q5_0.bin` as string
   literals scattered through the new class. These are environment
   paths (per human.md: prefer a map of defaults over config files) —
   a small constants class or a single configuration point is enough;
   this doesn't need a general-purpose config file.
4. **Wire into `SpeechApplication.createDefault()`.** Swap
   `new WindowsSpeechInput()` / `new WindowsSpeechToText()` for the new
   local-whisper implementations, or make the choice selectable (see
   Open Questions).
5. **Update `AudioInput` and all call sites** for the new field shape.
   `WindowsSpeechInput`/`WindowsSpeechToText` must be updated to match
   or removed.

## Tests Required

- `LocalWhisperSpeechToText` transcription of a known-good WAV fixture
  (e.g. commit `jfk.wav` as a test resource) produces the expected
  text.
- Process failure handling: missing model file, missing `whisper-cli.exe`,
  non-zero exit code — should surface as `SpeechException`, not a raw
  crash, consistent with `WindowsSpeechProcess`'s existing error
  handling.
- Timeout / cancellation behavior mirrors `WindowsSpeechProcess`'s
  existing tests, if any exist — check `tst/speech/` for prior art
  before writing new tests from scratch.
- `AudioInput` record change: confirm nothing else in `tst/speech/`
  depended on the old `recognizedText` field shape.

## Non-Goals For This Pass

- No cross-platform abstraction yet (Linux/Mac whisper.cpp binaries).
  Windows-only for now; the subprocess pattern will port later, but
  don't build the abstraction until there's a second platform to
  support.
- No `whisper-server.exe` / persistent server mode. The CLI
  per-invocation subprocess pattern matches the existing
  `WindowsSpeechProcess` architecture and is simplest for now. A
  persistent server (avoiding ~800ms model-load time per call) is a
  reasonable future optimization once the CLI path works end-to-end.
- No streaming/partial transcription. Existing `LISTENING` →
  `TRANSCRIBING` two-phase state machine stays as-is.
- No change to `KeywordLlmRouter` or the LLM provider routing — that's
  an unrelated part of the codebase.

## Open Questions for Whoever Picks This Up

- Should the SAPI path stay available as a fallback (e.g. a settings
  toggle), or is local Whisper meant to fully replace it now that
  it's validated? Ask the user before ripping SAPI out.
- Where should the WAV capture temp file live and when does it get
  cleaned up? `WindowsSpeechProcess`-style temp files elsewhere in the
  codebase use `/tmp` conventions from `loop.sh` — check whether an
  equivalent Windows temp convention already exists in this repo.
- Model load time (~800ms per the CLI test) is paid on every
  invocation with the current CLI-subprocess design. Confirm this is
  acceptable for the "needs to happen quickly" requirement before
  building the server-mode alternative — if a per-command 800ms
  overhead added to the actual transcription time (~150ms encode) is
  fine, don't bother with server mode.

## Completion Criteria

A developer can press the microphone button in the Swing UI, speak a
command, and see an accurate transcript appear — powered by local
CUDA-accelerated Whisper instead of SAPI — with the existing
`CommandResponse` / LLM-routing pipeline downstream unchanged.

## Who Should Receive This Handoff

This is well-specified, self-contained Java implementation work in a
single small repo (`speech`). Suitable for **Claude Code or Codex
working directly in the `speech` repository**. Not architecturally
risky enough to need Antigravity/multi-agent orchestration — a single
agent session should be able to complete items 1–5 above in one pass,
though item 1 (raw WAV capture on Windows) is the one piece with real
unknowns and may need some exploration/iteration.
