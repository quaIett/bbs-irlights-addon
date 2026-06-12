<#
.SYNOPSIS
  Asserts that two GLSL-family files differ ONLY in comments / blank lines.

  Checks:
    1. After stripping /* */ blocks and // tails and dropping blank lines,
       both files must be line-identical (trailing whitespace ignored).
    2. Every Iris option-value line (a line containing '// [') must survive
       byte-identical (same lines, same order) - these are functional.

  Exit 0 + "OK" when both hold; exits 1 with a diff hint otherwise.

.EXAMPLE
  powershell -File tools\assert-comments-only.ps1 -Before $env:TEMP\bak\x.glsl -After Shadres\Modification\Bliss\shaders\lib\irlite\irlite_lights.glsl
#>
param(
    [Parameter(Mandatory=$true)][string]$Before,
    [Parameter(Mandatory=$true)][string]$After
)
$ErrorActionPreference = 'Stop'

function StripComments([string]$text) {
    $t = [regex]::Replace($text, '(?s)/\*.*?\*/', ' ')
    $t = [regex]::Replace($t, '//[^\r\n]*', '')
    $lines = ($t -split "\r?\n") | ForEach-Object { $_.TrimEnd() } | Where-Object { $_ -ne '' }
    return ($lines -join "`n")
}
function OptionLines([string]$text) {
    # Only count lines where '// [' appears as a TRAILING comment on a code line
    # (i.e. the line is not a pure comment line starting with //). Pure comment
    # lines that happen to contain '[' are not Iris option-value lines.
    return (($text -split "\r?\n") | Where-Object { $_ -match '//\s*\[' -and $_.TrimStart() -notmatch '^//' } | ForEach-Object { $_.Trim() }) -join "`n"
}

$b = [System.IO.File]::ReadAllText($Before)
$a = [System.IO.File]::ReadAllText($After)

$sb = StripComments $b
$sa = StripComments $a
if ($sb -cne $sa) {
    Write-Output "FAIL: code differs (comments stripped) for $After"
    $lb = $sb -split "`n"; $la = $sa -split "`n"
    $n = [Math]::Max($lb.Count, $la.Count)
    for ($i = 0; $i -lt $n; $i++) {
        $x = if ($i -lt $lb.Count) { $lb[$i] } else { '<EOF>' }
        $y = if ($i -lt $la.Count) { $la[$i] } else { '<EOF>' }
        if ($x -cne $y) { Write-Output "  first mismatch at stripped-line $($i+1):"; Write-Output "    before: $x"; Write-Output "    after : $y"; break }
    }
    exit 1
}

$ob = OptionLines $b
$oa = OptionLines $a
if ($ob -cne $oa) {
    Write-Output "FAIL: Iris option-value lines ('// [') changed in $After"
    exit 1
}

Write-Output "OK: $After (comments-only change)"
exit 0
