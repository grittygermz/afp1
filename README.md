# AFP GOCA Converter — `afp-converter.jar`

Replaces legacy **IM** (Image) raster data inside AFP files with modern
**GOCA** (Graphics Object Content Architecture) vector filled rectangles.

The input AFP file must contain at least one IM image object — a
`BII…EII` block with `ICP` (Image Cell Position) and `IRD` (Image Raster
Data) structured fields.  Each ICP cell is converted to a GOCA grey-filled
rectangle (`GBAR` + `GBOX` + `GEAR`) that covers the same page area as the
original raster tile.

---

## Table of Contents

1. [Project structure](#1-project-structure)
2. [How to build and run](#2-how-to-build-and-run)
3. [How it works](#3-how-it-works)
4. [Architecture & package overview](#4-architecture--package-overview)
5. [Key design decisions](#5-key-design-decisions)
6. [Supported file structures](#6-supported-file-structures)
7. [GOCA byte stream format](#7-goca-byte-stream-format)
8. [Regression testing](#8-regression-testing)
9. [Known issues & future work](#9-known-issues--future-work)

---

## 1. Project structure

```
.
├── pom.xml                          # Maven build (produces fat jar)
├── afp-converter.jar                # Built fat jar
├── in/                              # Place input files here
├── output/                          # Converted files (created at runtime)
├── log/                             # Run logs + tracking CSV (created at runtime)
├── compare_afp.py                   # Regression comparison script
├── src/main/java/com/afp/replacement/
│   ├── AfpColorReplacer.java        # Entry point + batch orchestrator
│   ├── model/
│   │   ├── ImageStrip.java          # ICP strip data
│   │   ├── PageInfo.java            # PGD + IOC + IID page parameters
│   │   └── SectionBounds.java       # BII/EII block boundaries
│   ├── parser/
│   │   └── AfpSectionParser.java    # AFP parsing (raw byte + afplib)
│   ├── goca/
│   │   ├── GocaStreamBuilder.java   # GOCA drawing-order byte stream
│   │   ├── GraphicsGroupWriter.java # BGR…EGR group writer
│   │   ├── ObdTemplateBuilder.java  # OBD from PageInfo
│   │   └── GddTemplateBuilder.java  # GDD from PageInfo
│   └── util/
│       ├── ByteWriter.java          # Big-endian write helpers
│       └── EbcdicEncoder.java       # ASCII → EBCDIC conversion
├── O1XB*.afp                        # Reference test files (originals + papy)
├── task.md / task2.md               # Requirements
└── README.md                        # This file
```

---

## 2. How to build and run

**Prerequisites:** Java 17+, Maven 3.6+.

### Build

```bash
mvn clean package
```

The fat jar (with all dependencies shaded in) is written to
`target/afp-color-replacer-1.0-SNAPSHOT.jar`.  Copy it wherever convenient:

```bash
cp target/afp-color-replacer-1.0-SNAPSHOT.jar afp-converter.jar
```

### Run

```bash
java -jar afp-converter.jar
```

The jar looks for an `in/` folder in **its own directory** (not the working
directory).  Every file in `in/` is processed.  Non-AFP files and AFP files
without `BII/EII` pairs are copied as-is to `output/` (logged as `skipped`).

Output is written to `output/`, logs to `log/`, and a tracking CSV to
`log/conversion_log_<timestamp>.csv`.

---

## 3. How it works

### Phase 1 — Reading

1. The entire input file is read into a `byte[]`.
2. **Raw byte scanning** (`AfpSectionParser.locateBiiEii()`) walks the file
   sequentially, recognising `5A` magic bytes.  It builds a list of
   `ImageBlock` objects — one per `BII…EII` pair.  Each block records:
   - `start` / `end` byte offsets in the raw file
   - `hasICP` — whether the block contains any `ICP` fields
3. **afplib parsing** (`AfpSectionParser.parseAfpFields()`) feeds the same
   bytes through `AfpInputStream`.  It extracts:
   - **PGD** → page dimensions and measurement units (into `PageInfo`)
   - **IOC** → origin offset stored on the current `ImageBlock`
     (each block has its own IOC offset; see §5)
   - **IID** → compute scale factor `PGD_units / IID_units`
     (only the **first** IID sets this; subsequent IIDs are ignored)
   - **ICP** → strip position, cell size, fill size (appended to the
     current block's `strips` list)
4. Raw byte scanning also finds the **EAG** (`D3A9C9`) end position, used
   for z-ordering (see §5).

### Phase 2 — Writing

1. The output starts with **front matter**: all bytes from the beginning of
   the file up to the end of EAG.
2. **GOCA groups** are emitted for every ICP-containing block, one
   `BGR…EGR` group per strip.  Each group contains:
   - `BGR` (Begin Graphics), `BOC`, `OBD`, `IOC`, `IID`, `GDD`, `EOC`
     — pre-built template bytes (constant except for OBD/GDD which are
     parameterised from the PGD)
   - `GAD` (Graphics Area Descriptor) — the raw GOCA drawing-order stream
     (prolog + colour + fill-box commands)
   - `EGR` (End Graphics)
3. **Grid content** (everything between EAG and the first BII —
   `BPT/PTX/EPT` text objects) follows the GOCA groups, so grid lines
   paint **on top** of the shaded areas.
4. **Preserved IRD-only blocks** — `BII…EII` blocks without any `ICP`
   entries, written as raw bytes.
5. **Trailer** (everything after the last `EII`) completes the file.

---

## 4. Architecture & package overview

### `com.afp.replacement`

#### `AfpColorReplacer.java`

Entry point.  Orchestrates the batch processing loop:

1. Resolves directories relative to the jar's location
2. Lists files in `in/`
3. For each file, delegates to `processOneFile()` which calls
   `AfpSectionParser.parse()` and then writes the output
4. Maintains the tracking CSV and dual console/file logging

#### `model/ImageStrip.java`

Data class for one ICP cell.  Holds:
- `originX`, `originY` — ICP `XCOset` / `YCOset`
- `cellWidth`, `cellHeight` — ICP `XCSize` / `YCSize`
- `fillWidth`, `fillHeight` — ICP `XFilSize` / `YFilSize`

The factory method `fromIcp(ICP)` extracts these from an afplib `ICP`.

#### `model/PageInfo.java`

Global page parameters populated during parsing:
- `xUnits`, `yUnits`, `xSize`, `ySize` — from PGD
- `xOriginOffset`, `yOriginOffset` — from the **first** IOC (used as
  fallback when a block has no IOC explicitly stored)
- `scaleX`, `scaleY` — computed as `PGD_units / IID_units` from the
  **first** IID; subsequent IIDs are ignored via `iidScaleSet` flag

All fields are mandatory — defaults were removed after an earlier
iteration caused silent mis-scaling.

#### `model/SectionBounds.java` + `ImageBlock`

Holds the result of raw-byte scanning:
- `frontEnd` — byte position after EAG (used for z-ordering split)
- `headerEnd` — byte position of the first BII's `5A`
- `trailerStart` — byte position after the last EII
- `imageBlocks` — list of `ImageBlock` objects

Each `ImageBlock` has:
- `start`, `end` — byte offsets of the `BII…EII` range
- `hasICP` — whether the block contained any `ICP` fields
- `strips` — populated during afplib parsing
- `xOffset`, `yOffset` — the IOC origin offset from this block's
  own `IOC` field; each block gets its own so multi-block files with
  varying offsets (e.g. `O1XB159G`) work correctly

#### `parser/AfpSectionParser.java`

Two‑pass parsing:

1. `locateBiiEii()` — raw byte scan.  Finds every `5A` magic, classifies
   each SF by its 3‑byte ID.  Builds `ImageBlock` list, determines
   `hasICP` by scanning each block's interior for `SF_ID_ICP`.

2. `parseAfpFields()` — afplib stream parser.  Walks all SFs and:
   - Captures PGD, IOC, IID, ICP into `PageInfo` / `ImageBlock`
   - Increments `blockIndex` on each `BII` and appends strips to
     `blocks[blockIndex].strips`

The split approach exists because afplib's `getOffset()` can be
inaccurate when reading from a `ByteArrayInputStream`, while the
raw scanner gives exact file offsets for header/trailer slicing.

#### `goca/GocaStreamBuilder.java`

Builds the GOCA byte stream for one strip.

The stream consists of:
- **Prolog** (21 bytes): `BeginSegmentCommand` + `NOP` + `GSLE` +
  `GSLT` + `GSMT`.  Taken from the `O1XB3131_papy.afp` reference file.
  Required to initialise the GOCA interpreter state.
- **GSPCOL** (15 bytes): Set Process Color — Device RGB, 8 bits per
  component, COLVALUE = `{R=240, G=240, B=240}` (light grey).
  Uses the `0xB2` GSPCOL command rather than the older `GSECOL` (`0x26`)
  which only supports a 2‑byte indexed colour that AFP viewers reject
  for arbitrary RGB values.
- **GBAR** (2 bytes): Begin Area, fill flag = `0x80`.
- **GSCOL** (2 bytes): Set Color to 0 (black) for the outline.
- **GBOX** (12 bytes): Rectangle (compact form `0xC0`).  Coordinates
  are **flipped** from AFP Y‑down to GOCA Y‑up using `pageHeight`.
- **GEAR** (2 bytes): End Area — matches the `GBAR`.

Coordinates and fill sizes are first scaled from IID units to page
units using the `scaleX/scaleY` factor.

#### `goca/GraphicsGroupWriter.java`

Writes one 9‑field `BGR…EGR` group.  The static fields (`BGR`, `BOC`,
`IOC`, `IID`, `EOC`, `EGR`) are pre‑built as byte-array templates via
`buildConstantSf()`.  `OBD` and `GDD` are built at construction time
from PageInfo.  The `GAD` is built per‑strip.

The `write()` method receives per‑block `xOffset`/`yOffset` so that
files with multiple image blocks (each with a different IOC) are handled
correctly.

#### `goca/ObdTemplateBuilder.java` / `GddTemplateBuilder.java`

Construct the `OBD` and `GDD` structured-field byte arrays from the
`PageInfo` measurement units and dimensions.  Both are parameterised
at runtime so that files with different PGD units (2400, 14400, …) get
matching OBD/GDD values.

#### `util/ByteWriter.java`

Big-endian binary helpers: `write16be`, `write24be`, `write16`, `write24`,
`copyInto`.

#### `util/EbcdicEncoder.java`

Converts ASCII strings to 8‑byte EBCDIC fields padded with `0x40`
(EBCDIC space character) for AFP object names.

---

## 5. Key design decisions

### 5.1 — Why raw bytes instead of afplib serialisation?

The graphics wrapper we emit contains structured fields whose SF IDs
(`OBD` = `D3A66B`, `IOC` = `D3AC6B`, `IID` = `D3ABBB`, `GDD` = `D3A6BB`)
are not fully handled by afplib's `binary()` serialiser.  When afplib
reads them, they become `UNKNSF` objects whose `.getRawData()` includes
the `5A` magic byte.  When `binary()` writes them back, it copies the
raw data into the buffer, but `AfpOutputStream` also writes its own `5A`
header — producing a duplicated `5A` that corrupts the file.

Therefore afplib is used **only for reading/parsing**.  The output is
written as raw bytes via `FileOutputStream`.

### 5.2 — Why split into `frontEnd` + grid + GOCA groups?

In AFP, objects are painted in data-stream order — later objects appear
**on top** of earlier ones.  The original files place grid lines
(`BPT/PTX/EPT` text) **before** the image (`BII`), so the image shades
over the grid.

Our GOCA groups replace the image.  To keep the grid visible **on top**
of the shading, we insert the GOCA groups **between** EAG and the grid
content, not at the BII position.  Three byte ranges are sliced from the
original file: `front` (up to EAG), `grid` (EAG to first BII), and
`trailer` (after last EII).  Output order: `front → GOCA → grid →
[blocks] → trailer`.

### 5.3 — Per‑block IOC offsets

Some files (e.g. `O1XB159G`) have one `BII/EII` block per strip, each
with its own `IOC` at a different `yoaOset`.  Storing a single global
IOC offset (`PageInfo.xOriginOffset`) would cause all strips to use the
last block's offset.  Each `ImageBlock` therefore stores its own
`xOffset`/`yOffset` from its own `IOC`, and `GraphicsGroupWriter.write()`
accepts per‑block offsets.

### 5.4 — IID scale captured only from first IID

Files with multiple `BII/EII` blocks (e.g. `O1XB0108` has two; the
second is IRD-only) may have different `IID` measurement units.  The
scale factor (`PGD_units / IID_units`) is set **once** from the first
`IID` encountered and never overwritten (`iidScaleSet` flag in
`PageInfo`).  IRD-only blocks are preserved as raw bytes and are not
affected by scaling.

### 5.5 — Fill sizes vs cell sizes

Every `ICP` has two sets of dimensions:
- **Cell size** (`XCSize`/`YCSize`) — the per-band tile, typically 32×8.
- **Fill size** (`XFilSize`/`YFilSize`) — the actual rendered extent,
  which can be much larger (e.g. 2702 units tall).

The converter uses **fill sizes** when non-zero, falling back to cell
sizes only when fill is absent.  The GOCA box dimensions are:
```
boxWidth  = (fillWidth  > 0) ? fillWidth  : cellWidth
boxHeight = (fillHeight > 0) ? fillHeight : cellHeight
```

Both are then scaled by the IID-to-page factor.

### 5.6 — Y‑coordinate flip

AFP page coordinates have Y increasing **downward** (origin at top-left).
GOCA graphics coordinates have Y increasing **upward** (origin at
bottom-left).  The conversion is:
```java
gocaY0 = pageHeight - (afpY + boxHeight)   // bottom edge
gocaY1 = pageHeight - afpY                  // top edge
```

### 5.7 — GEAR closing and GBOX command ID

Every `GBAR` (Begin Area) must be matched by a `GEAR` (End Area,
`0x60 0x00`).  Without it, AFP viewers report `EC-6800 Unclosed area`.

The GBOX drawing order must use **command ID `0xC0`** (compact GBOX,
id = 192).  Using `0x91` (which is `GCBIMG`, Begin Image in GOCA) causes
viewers to misinterpret the box data as an image mapping command.

---

## 6. Supported file structures

### Single‑block ICP (most common)

```
BMO … EAG → BII → IOC → IID → [ICP → IRD]×N → EII → EMO
```

Replaced by N GOCA groups.

### Multi‑block ICP (e.g. O1XB159G)

```
BMO … EAG → [BII → IOC → IID → ICP → IRD → EII]×N → EMO
```

Each of the N blocks has its own IOC with a different `yoaOset`.
All N strips are collected across all blocks and each is drawn with
its own IOC offset.

### ICP + IRD‑only mixed (e.g. O1XB0108)

```
BMO … EAG → BII → IOC → IID → [ICP → IRD]×N → EII
           → BII → IOC → IID → IRD → EII → EMO
```

First block (ICP) is replaced with GOCA groups.  Second block (no ICP)
is preserved verbatim as raw bytes.

### Files with grid lines (BPT/PTX/EPT)

```
BMO … EAG → BPT → PTX → EPT → BII → …
```

GOCA groups are inserted after EAG but **before** BPT, so grid text
paints on top of the shading.

---

## 7. GOCA byte stream format

Each `GAD` field carries the following byte stream (`GocaStreamBuilder`):

| Offset | Length | Contents |
|--------|--------|----------|
| 0 | 2 | BeginSegmentCommand: `70 0C` |
| 2 | 9 | Segment name + flags: zeros + `00 27` + zeros |
| 11 | 4 | PSNAME: zeros |
| 15 | 1 | NOP: `00` |
| 16 | 2 | GSLE (Set Line End): `19 00` |
| 18 | 2 | GSLT (Set Line Type): `18 07` |
| 20 | 2 | GSMT (Set Marker Type): `28 10` |
| **22** | **15** | **GSPCOL** (Set Process Color): `B2 0D 00 01 00 00 00 00 08 08 08 00 R G B` |
| **37** | **2** | **GBAR** (Begin Area): `68 80` |
| **39** | **2** | **GSCOL** (Set Color outline): `0A 00` |
| **41** | **12** | **GBOX** (Box compact): `C0 0A 00 20 [X0(2)] [Y0(2)] [X1(2)] [Y1(2)]` |
| **53** | **2** | **GEAR** (End Area): `60 00` |

**Total:** 55 bytes per strip.

The GBOX `X0`/`Y0`/`X1`/`Y1` are signed 16‑bit big-endian integers.
The coordinates are:
```
X0 = (ICP.XCOset  + IOC.xoaOset) × scaleX
Y0 = pageHeight − ((ICP.YCOset + IOC.yoaOset + boxHeight) × scaleY)   [Y-flip]
X1 = X0 + boxWidth
Y1 = pageHeight − ((ICP.YCOset + IOC.yoaOset) × scaleY)               [Y-flip]
```

Where `scaleX = PGD.xUnits / IID.xUnits`, `scaleY = PGD.yUnits / IID.yUnits`,
and `boxWidth`/`boxHeight` are the fill (or cell) sizes scaled similarly.

---

## 8. Regression testing

The `compare_afp.ps1` script compares two directories of output `.afp`
files by full byte-level hex diff.  Any change — in structured fields,
coordinates, or incidental data — is detected.

```powershell
# Reference run (the known-good version):
java -jar afp-converter.jar
Rename-Item output output-ref

# Candidate run (after code changes):
java -jar afp-converter.jar
Rename-Item output output-cand

# Compare (PowerShell 7+):
pwsh -NoProfile -File compare_afp.ps1 -Ref output-ref -Cand output-cand
```

For hex-dump details of differing regions:
```powershell
pwsh -NoProfile -File compare_afp.ps1 -Ref output-ref -Cand output-cand -ShowDiff
```

To control context lines around each difference (default 2):
```powershell
pwsh -NoProfile -File compare_afp.ps1 -Ref output-ref -Cand output-cand -ShowDiff -Context 3
```

What the script shows per-file:
- Structural summary (SF count, blocks, strips, GOCA groups)
- Hex-dump lines common/different between the two versions
- Byte-level position, hex values, and ASCII representation

### Golden test files

The repository includes these reference files in the project root:

| File | Strips | Blocks | Special characteristics |
|------|--------|--------|------------------------|
| `O1XB0024_original.afp` | 5 | 1 | Has grid lines (BPT/PTX/EPT); IOC xoaOset=0 |
| `O1XB0009_original.afp` | 31 | 1 | IOC xoaOset=198 (non-zero offset) |
| `O1XB3130_original.afp` | 39 | 1 | Vertical strips, XFilSize=1568, YFilSize=26 |
| `O1XB3131_original.afp` | 20 | 1 | Horizontal strips, XFilSize=30, YFilSize=2702 |
| `O1XB0046_original.afp` | 17 | 1 | PGD=11904×16836 at 14400 Ln/in (IID/PGD mismatch→scale=6) |
| `O1XB0047_original.afp` | 17 | 1 | Same as 0046 |
| `O1XB0108_original.afp` | 16 | 2 | Two blocks: first has ICP, second is IRD-only |
| `O1XB159G_original.afp` | 16 | 16 | Each strip has its own IOC with unique yoaOset |

---

## 9. Known issues & future work

- **Multiple different IIDs**: If multiple ICP-containing blocks have
  different IID measurement units, only the first block's IID is used
  for scaling.  This hasn't been encountered in practice.
- **IOC xMap/yMap**: The IOC's `xMap`/`yMap` (image-to-page mapping
  percentages) are currently ignored.  All tested files have them at
  1000 (= 100.0%), so this has not been an issue.
- **GSCOL outline colour**: The outline is hardcoded to black (`0A 00`).
  This could be made configurable.
- **Fill colour**: Hardcoded to grey (240,240,240).  Configurable via
  constants in `GocaStreamBuilder.java`.
- **Object name**: Hardcoded to `OGL GOCA` in `GraphicsGroupWriter.java`.
- **OBD/GDD templates**: The non-measurement portions of the OBD and GDD
  are taken verbatim from `O1XB3131_papy.afp`.  They may need tuning
  for files with unusual coordinate systems.
- **ICPs without fill sizes**: Falls back to cell sizes.  All tested
  files have fill sizes, but some real-world files may not.
