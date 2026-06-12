param(
    [string]$ShadersRoot = "C:\Users\Qualet\Documents\Project\Minecraft\BBS\IRLite\Shadres\Modification\Solas\shaders"
)

$dir = "C:\Users\Qualet\AppData\Local\Temp\irisprop"
$jcpp = "C:\Users\Qualet\.gradle\caches\modules-2\files-2.1\org.anarres\jcpp\1.4.14\f9ac1dbd3101dd1fe01d1d3bcd1481193cf5c113\jcpp-1.4.14.jar"
$slf = "C:\Users\Qualet\.gradle\caches\modules-2\files-2.1\org.slf4j\slf4j-api\1.7.12\8e20852d05222dc286bf1c71d78d0531e177c317\slf4j-api-1.7.12.jar"

function Resolve-Glsl {
    param([string]$File, [int]$Depth, [System.Collections.Generic.List[string]]$Out)
    if ($Depth -gt 12) { throw "include depth exceeded at $File" }
    foreach ($line in [System.IO.File]::ReadAllLines($File)) {
        if ($line -match '^\s*#include\s+"(.+)"') {
            $p = Join-Path $ShadersRoot ($Matches[1].TrimStart('/') -replace '/', '\')
            Resolve-Glsl -File $p -Depth ($Depth + 1) -Out $Out
        } else {
            $Out.Add($line)
        }
    }
}

$programs = Get-ChildItem $ShadersRoot -File | Where-Object { $_.Extension -in '.fsh', '.vsh', '.csh' }
Write-Host "testing $($programs.Count) root programs from $ShadersRoot"
$fails = 0
foreach ($prog in $programs) {
    $lines = New-Object 'System.Collections.Generic.List[string]'
    Resolve-Glsl -File $prog.FullName -Depth 0 -Out $lines
    $tmp = Join-Path $dir "concat.glsl"
    [System.IO.File]::WriteAllLines($tmp, $lines)
    $res = & java -cp "$dir;$jcpp;$slf" GlslTest $tmp 2>$null
    if ($res) {
        $fails++
        Write-Host "=== FAIL $($prog.Name) [$($lines.Count) lines]:"
        $res | Select-Object -First 4 | ForEach-Object { Write-Host "    $_" }
        # map reported line back to file: dump the offending line text
        foreach ($r in ($res | Select-Object -First 1)) {
            if ($r -match '@(\d+),') {
                $ln = [int]$Matches[1]
                if ($ln -le $lines.Count) { Write-Host "    concat L$ln = $($lines[$ln-1])" }
            }
        }
    }
}
Write-Host "done, failures: $fails"
