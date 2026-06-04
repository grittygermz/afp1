Based on our technical deep-dive into the internals of the IBM AFP architecture, here is a detailed breakdown of the exact real-world problem you are trying to solve, why your initial attempts hit a wall, and the exact engineering blueprint required to achieve your goal.

---

## 1. The Core Objective (What You Are Trying to Do)

You have a production-level **AFP (Advanced Function Presentation)** print file. Inside this document, there is a specific, visually "bounded area" (like a colored background banner, a highlight box, or a layout shape) that you need to programmatically modify to change its color to a specific **RGB value**.

### The Hidden Complication

You discovered that this bounded area isn't a modern vector graphic. It is built using legacy **IM Image architecture**.

Instead of being an easily editable shape, your target area is a black-and-white bitmap grid composed of an **IMLS (Image Move Line Space)** positioning instruction, an **ICP (Image Cell Position)** bounding box, and an **IRD (Image Raster Data)** stream of raw binary `1` and `0` pixel dots.

### Why Common Fixes Failed

* **Python (`mdneale/afp`):** This library is read-only. It can tell you where the image is, but it cannot serialize or rebuild the file if you try to modify it.
* **The PDF Workaround:** Converting the AFP to PDF flattened the entire document layout into a massive, heavy background image. When converted back to AFP, it broke the printer's layout logic and optimization behavior.

---

## 2. The Proposed Solution: "Structural Architecture Replacement"

To achieve your goal without corrupting the print stream or bloat file sizes, you must perform a low-level binary upgrade: **Completely strip out the legacy raster IM Image data and dynamically replace it with a modern GOCA (Graphics Object Content Architecture) vector shape.**

Instead of wrestling with raw pixel grids, you convert the entire box into a clean mathematical primitive that natively supports RGB colors.

---

## 3. Detailed Step-by-Step Execution Plan

To execute this safely, you should use **`afplib` (Java)** because it allows you to manipulate the document tree as programmable objects and handles all the complex binary length recalculations automatically.

### Phase 1: The Coordinate Audit

Because legacy IM images use relative spacing (`IMLS`) to push the drawing cursor down the page, your code must read the document sequentially from the top.

* Track the cumulative vertical space applied by any preceding text (`PTOCA`) or line spaces (`IMLS`).
* When you hit the target `ImageCellPosition (ICP)` field, record its exact width, height, and computed $X, Y$ coordinate offsets on the page. This gives you the precise bounding coordinates for your new box.

### Phase 2: Surgical Extraction

As `afplib` reads the file stream into memory, program your loop to **omit** the following structured fields associated with that specific image block:

* `BeginImageObject (BIM)`
* `ImageOutputControl (IOC)`
* `ImageCellPosition (ICP)`
* `ImageRasterData (IRD)`
* `EndImageObject (EIM)`

By filtering these out, you effectively delete the old black-and-white pixel box from the document layout.

### Phase 3: GOCA Vector Injection

In the exact structural spot where the old image used to live, use `afplib`'s factory to inject a brand-new **Graphics Object Container**. Inside this container, you will programmatically draw a vector rectangle using the coordinates you saved in Phase 1.

You will append three essential drawing commands inside the new GOCA container:

1. **`SetExtendedColor`**: Use `240, 240, 240` for RGB value (which should be grey colour)
2. **`BeginArea`**: Tell the rendering engine that you want the following shape to be completely filled with that color, rather than just an empty outline.
3. **`Box`**: Pass the exact bottom-left and top-right $X,Y$ spatial coordinates.

### Phase 4: Auto-Serialization

Once the modifications to the object list are complete, pass the modified Java object tree to `AfpOutputStream`.

`afplib` will dynamically look at your new GOCA blocks, calculate exactly how many bytes they take up, rewrite all the mandatory binary field headers (`5A 00 00...`), update the parent page length constraints, and stream out a perfectly valid, optimized, uncorrupted AFP file.

---

## The Ultimate Outcome

By changing the task from a *pixel-painting problem* to an *architectural replacement framework*, you achieve a file that handles perfectly on industrial print spoolers, renders with microscopic sharpness at any DPI, and gives you direct code control over the exact RGB presentation.

## Additional resources provided
I have provided in the resources folder 2 .afp files
1) O1XB3131_original.afp is the original file
2) O1XB3131_papy.afp is the expected output

use the pom.xml file provided for your java project
question the above instructions provided as i am unfamiliar with the afp format
verify your work
O1XB3131_papy.afp is a human modified files and while parsing it with afplib you may encounter some errors but it should serve as a guide