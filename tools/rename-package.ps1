<#
.SYNOPSIS
  Renames a Java package prefix across a repo tree.

  Does three things, in order:
    1. Text replace in all (non-binary) files: the dotted form (org.wemppy), the
       slash form (org/wemppy) and the backslash form (org\wemppy) of -From are
       replaced with the matching form of -To.
    2. Renames files whose NAME contains the dotted -From (e.g. mixin configs).
    3. Moves package directory trees: any dir whose path ends with the -From
       package path (e.g. src\main\java\org\wemppy) has its children moved to
       the -To package path next to it, then the emptied source dirs are removed.

  Byte-safety: files are read and written through a Latin-1 (ISO-8859-1) round
  trip, which maps bytes 1:1 to chars. Only the matched ASCII runs change; every
  other byte (encoding, EOLs, BOM-less UTF-8 multibyte sequences) survives
  untouched. UTF-16 files (BOM-detected) are handled via their own encoding.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\rename-package.ps1 -Root . -DryRun
  powershell -ExecutionPolicy Bypass -File tools\rename-package.ps1 -Root . -From org.wemppy -To qualet
#>
param(
    [string]$Root = ".",
    [string]$From = "org.wemppy",
    [string]$To   = "qualet",
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path -LiteralPath $Root).Path.TrimEnd('\')

$fromSlash = $From.Replace('.', '/')
$toSlash   = $To.Replace('.', '/')
$fromBack  = $From.Replace('.', '\')
$toBack    = $To.Replace('.', '\')

$binaryExt = @('.jar','.zip','.class','.png','.jpg','.jpeg','.gif','.ico','.dll','.exe','.bin','.dat','.gz','.ogg','.wav')
$excludeRx = '\\(\.git|\.gradle|\.idea|build|run|bin|out)(\\|$)'
$latin1 = [System.Text.Encoding]::GetEncoding(28591)

function Replace-AllForms([string]$text) {
    return $text.Replace($From, $To).Replace($fromSlash, $toSlash).Replace($fromBack, $toBack)
}

# ---------- collect candidate files ----------
$files = @()
if (Test-Path -LiteralPath (Join-Path $Root '.git')) {
    $rel = git -C $Root ls-files
    if ($LASTEXITCODE -ne 0) { throw "git ls-files failed in $Root" }
    $files = $rel | ForEach-Object { Join-Path $Root ($_.Replace('/', '\')) }
} else {
    $files = Get-ChildItem -LiteralPath $Root -Recurse -File |
        Where-Object { $_.FullName -notmatch $excludeRx } |
        ForEach-Object { $_.FullName }
}

# ---------- 1) text replace ----------
$patched = New-Object System.Collections.Generic.List[string]
foreach ($f in $files) {
    if (-not (Test-Path -LiteralPath $f)) { continue }
    $ext = [System.IO.Path]::GetExtension($f).ToLowerInvariant()
    if ($binaryExt -contains $ext) { continue }

    $bytes = [System.IO.File]::ReadAllBytes($f)
    if ($bytes.Length -eq 0) { continue }

    $enc = $latin1
    if ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFF -and $bytes[1] -eq 0xFE) { $enc = [System.Text.Encoding]::Unicode }
    elseif ($bytes.Length -ge 2 -and $bytes[0] -eq 0xFE -and $bytes[1] -eq 0xFF) { $enc = [System.Text.Encoding]::BigEndianUnicode }

    $raw = $enc.GetString($bytes)
    $new = Replace-AllForms $raw
    if ($new -ne $raw) {
        $patched.Add($f)
        if (-not $DryRun) {
            $outBytes = $enc.GetBytes($new)
            if ($enc -ne $latin1) {
                # re-attach the BOM that GetBytes does not emit
                $outBytes = $enc.GetPreamble() + $outBytes
                if ($raw.Length -gt 0 -and $raw[0] -eq [char]0xFEFF) {
                    # GetString kept the BOM as a char; avoid doubling it
                    $outBytes = $enc.GetBytes($new)
                }
            }
            [System.IO.File]::WriteAllBytes($f, $outBytes)
        }
    }
}

# ---------- 2) rename files whose name contains the dotted From ----------
$renamed = New-Object System.Collections.Generic.List[string]
foreach ($f in @($files)) {
    if (-not (Test-Path -LiteralPath $f)) { continue }
    $leaf = Split-Path $f -Leaf
    if ($leaf.Contains($From)) {
        $newLeaf = $leaf.Replace($From, $To)
        $renamed.Add("$f -> $newLeaf")
        if (-not $DryRun) { Rename-Item -LiteralPath $f -NewName $newLeaf }
    }
}

# ---------- 3) move package directories ----------
$moved = New-Object System.Collections.Generic.List[string]
$pkgDirs = Get-ChildItem -LiteralPath $Root -Recurse -Directory |
    Where-Object {
        $_.FullName -notmatch $excludeRx -and
        ($_.FullName -eq (Join-Path $Root $fromBack) -or $_.FullName.EndsWith('\' + $fromBack))
    }

foreach ($d in $pkgDirs) {
    $src    = $d.FullName
    $base   = $src.Substring(0, $src.Length - $fromBack.Length)  # keeps trailing '\'
    $target = $base + $toBack
    $moved.Add("$src -> $target")
    if ($DryRun) { continue }

    New-Item -ItemType Directory -Force -Path $target | Out-Null
    Get-ChildItem -LiteralPath $src | Move-Item -Destination $target

    # remove the now-empty source dir and any emptied parents up to the base
    $cur  = $src
    $stop = $base.TrimEnd('\')
    while ($cur.Length -gt $stop.Length -and -not (Get-ChildItem -LiteralPath $cur)) {
        Remove-Item -LiteralPath $cur
        $cur = Split-Path $cur -Parent
    }
}

# ---------- report ----------
$mode = ''
if ($DryRun) { $mode = ' (dry run, nothing written)' }
Write-Output "rename-package$($mode): '$From' -> '$To' under $Root"
Write-Output "  files text-patched : $($patched.Count)"
Write-Output "  files renamed      : $($renamed.Count)"
Write-Output "  package dirs moved : $($moved.Count)"
foreach ($m in $moved)   { Write-Output "    dir : $m" }
foreach ($r in $renamed) { Write-Output "    file: $r" }
if ($DryRun) {
    foreach ($p in $patched) { Write-Output "    text: $p" }
}

# ---------- verification (skipped on dry run) ----------
if (-not $DryRun) {
    $leftover = New-Object System.Collections.Generic.List[string]
    $checkFiles = Get-ChildItem -LiteralPath $Root -Recurse -File |
        Where-Object { $_.FullName -notmatch $excludeRx -and ($binaryExt -notcontains $_.Extension.ToLowerInvariant()) }
    foreach ($cf in $checkFiles) {
        $raw = $latin1.GetString([System.IO.File]::ReadAllBytes($cf.FullName))
        if ($raw.Contains($From) -or $raw.Contains($fromSlash) -or $raw.Contains($fromBack)) {
            $leftover.Add($cf.FullName)
        }
    }
    if ($leftover.Count -gt 0) {
        Write-Output "  LEFTOVER occurrences of '$From' (manual review needed):"
        foreach ($l in $leftover) { Write-Output "    $l" }
    } else {
        Write-Output "  verification: no '$From' occurrences remain."
    }
}
