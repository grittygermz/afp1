<#
.SYNOPSIS
    AFP regression comparison tool — pure PowerShell.

.DESCRIPTION
    Compares two sets of AFP output files byte-by-byte and reports
    differences in human-readable hex dump format.  Useful for verifying
    that code changes haven't silently altered the output of previously
    correct files.

.PARAMETER Ref
    Directory containing the reference (known-good) output files.

.PARAMETER Cand
    Directory containing the candidate (new) output files.

.PARAMETER ShowDiff
    Show hex dump details for every differing file.

.PARAMETER Context
    Number of context lines around each difference (default 2).

.EXAMPLE
    .\compare_afp.ps1 -Ref output-ref -Cand output-cand
    .\compare_afp.ps1 -Ref output-ref -Cand output-cand -ShowDiff -Context 3
#>

param(
    [Parameter(Mandatory = $true)]
    [string]$Ref,
    [Parameter(Mandatory = $true)]
    [string]$Cand,
    [switch]$ShowDiff,
    [int]$Context = 2
)

# ──────────────────────────────────────────────────────────────────────
#  Helper functions
# ──────────────────────────────────────────────────────────────────────

function Get-HexLine($bytes, $offset, $width = 16) {
    $end = [Math]::Min($offset + $width, $bytes.Length)
    $hex = ''
    $asc = ''
    for ($i = $offset; $i -lt $end; $i++) {
        $hex += '{0:X2} ' -f $bytes[$i]
        $b = $bytes[$i]
        $asc += if ($b -ge 32 -and $b -lt 127) { [char]$b } else { '.' }
    }
    $offStr = '{0:X8}' -f $offset
    return '{0}  {1,-47}  |{2}|' -f $offStr, $hex.TrimEnd(), $asc
}

function Get-HexDump($bytes) {
    $lines = @()
    for ($i = 0; $i -lt $bytes.Length; $i += 16) {
        $lines += Get-HexLine $bytes $i
    }
    return $lines
}

function Get-StructuralSummary($path) {
    $bytes = [System.IO.File]::ReadAllBytes((Get-Item $path).FullName)
    $sfCount = 0; $blocks = 0; $strips = 0; $gads = 0
    $inBii = $false
    $pos = 0
    while ($pos -le $bytes.Length - 10) {
        if ($bytes[$pos] -ne 0x5A) { $pos++; continue }
        [int]$len = ($bytes[$pos+1] -shl 8) -bor $bytes[$pos+2]
        [int]$b3 = $bytes[$pos+3]; [int]$b4 = $bytes[$pos+4]; [int]$b5 = $bytes[$pos+5]
        [int]$id = ($b3 -shl 16) -bor ($b4 -shl 8) -bor $b5
        $sfCount++
        if    ($id -eq 0xD3A87B) { $blocks++; $inBii = $true }
        elseif($id -eq 0xD3A97B) { $inBii = $false }
        if ($inBii -and $id -eq 0xD3AC7B) { $strips++ }
        if ($id -eq 0xD3EEBB) { $gads++ }
        $pos += $len + 1
    }
    return "$sfCount SFs, $blocks blocks, $strips strips, $gads GOCA groups"
}

# ──────────────────────────────────────────────────────────────────────
#  Diff logic
# ──────────────────────────────────────────────────────────────────────

function Get-DiffEntries($refLines, $candLines) {
    $diffs = @()
    $equal = $true
    $max = [Math]::Max($refLines.Count, $candLines.Count)
    $i = 0
    while ($i -lt $max) {
        if ($i -ge $refLines.Count) {
            $diffs += ,@('added', $candLines[$i])
            $equal = $false
        } elseif ($i -ge $candLines.Count) {
            $diffs += ,@('removed', $refLines[$i])
            $equal = $false
        } elseif ($refLines[$i] -ne $candLines[$i]) {
            # find the differing region
            $rStart = $i; $cStart = $i
            $rEnd = $i; $cEnd = $i
            # scan forward
            while ($i -lt $max) {
                $rOk = $i -lt $refLines.Count
                $cOk = $i -lt $candLines.Count
                if ($rOk -and $cOk -and $refLines[$i] -eq $candLines[$i]) { break }
                $rEnd = $i; $cEnd = $i
                $i++
            }
            $equal = $false
            # context before
            $ctxStart = [Math]::Max(0, $rStart - $Context)
            for ($j = $ctxStart; $j -lt $rStart; $j++) {
                if ($j -lt $refLines.Count) { $diffs += ,@('context', $refLines[$j]) }
            }
            if ($ctxStart -lt $rStart) { $diffs += ,@('ctxsep', $null) }
            # removed lines
            for ($j = $rStart; $j -le $rEnd -and $j -lt $refLines.Count; $j++) {
                $diffs += ,@('removed', $refLines[$j])
            }
            # added lines
            for ($j = $cStart; $j -le $cEnd -and $j -lt $candLines.Count; $j++) {
                $diffs += ,@('added', $candLines[$j])
            }
            # context after
            for ($j = $rEnd + 1; $j -le $rEnd + $Context -and $j -lt $refLines.Count; $j++) {
                $diffs += ,@('context', $refLines[$j])
            }
            if ($rEnd + 1 -le $rEnd + $Context -and $rEnd + 1 -lt $refLines.Count) { $diffs += ,@('ctxsep', $null) }
        }
        $i++
    }
    if ($equal) { return @() }
    return $diffs
}

# ──────────────────────────────────────────────────────────────────────
#  Main
# ──────────────────────────────────────────────────────────────────────

if (-not (Test-Path $Ref -PathType Container)) {
    Write-Error "Reference directory not found: $Ref"
    exit 1
}
if (-not (Test-Path $Cand -PathType Container)) {
    Write-Error "Candidate directory not found: $Cand"
    exit 1
}

$refFiles = @(Get-ChildItem $Ref -Filter '*.afp' | Select-Object -ExpandProperty Name)
$candFiles = @(Get-ChildItem $Cand -Filter '*.afp' | Select-Object -ExpandProperty Name)

$common = $refFiles | Where-Object { $_ -in $candFiles } | Sort-Object
$onlyRef = $refFiles | Where-Object { $_ -notin $candFiles } | Sort-Object
$onlyCand = $candFiles | Where-Object { $_ -notin $refFiles } | Sort-Object

$anyDiff = $false

if ($onlyRef.Count -gt 0) {
    Write-Host "Only in reference: $($onlyRef -join ', ')" -ForegroundColor Yellow
    $anyDiff = $true
}
if ($onlyCand.Count -gt 0) {
    Write-Host "Only in candidate: $($onlyCand -join ', ')" -ForegroundColor Yellow
    $anyDiff = $true
}

foreach ($fname in $common) {
    $refPath = Join-Path $Ref $fname
    $candPath = Join-Path $Cand $fname

    $refBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $refPath))
    $candBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $candPath))

    if (Compare-Object $refBytes $candBytes -SyncWindow 0) { $differs = $true } else { $differs = $false }

    if (-not $differs) {
        if ($ShowDiff) {
            $summary = Get-StructuralSummary $refPath
            Write-Host "  [OK] $fname  ($summary)" -ForegroundColor Green
        }
        continue
    }

    $anyDiff = $true
    $refSummary = Get-StructuralSummary $refPath
    $candSummary = Get-StructuralSummary $candPath

    Write-Host "`n  [DIFF] $fname" -ForegroundColor Red
    Write-Host "    ref:  $refSummary"
    Write-Host "    cand: $candSummary"

    if (-not $ShowDiff) {
        Write-Host "    (use -ShowDiff to see hex diff)"
        continue
    }

    $refLines = Get-HexDump $refBytes
    $candLines = Get-HexDump $candBytes
    $entries = Get-DiffEntries $refLines $candLines

    foreach ($entry in $entries) {
        $kind = $entry[0]
        $line = $entry[1]
        if ($kind -eq 'context') {
            Write-Host "   $line"
        } elseif ($kind -eq 'ctxsep') {
            Write-Host "   ..."
        } elseif ($kind -eq 'removed') {
            Write-Host "-  $line" -ForegroundColor Red
        } elseif ($kind -eq 'added') {
            Write-Host "+  $line" -ForegroundColor Green
        }
    }
}

if (-not $anyDiff) {
    Write-Host "All files match — no regressions detected." -ForegroundColor Green
    exit 0
}

Write-Host ""
exit 1
