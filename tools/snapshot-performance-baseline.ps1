param(
    [string]$ShaderPack = 'ComplementaryReimagined_r5.8.1_IRLights',
    [string]$ReferencePack = 'ComplementaryReimagined',
    [string]$RunDirectory,
    [string]$OutputJson
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$core = Join-Path (Split-Path -Parent $repo) 'irl-core'
if (-not $RunDirectory) { $RunDirectory = Join-Path $repo 'run' }
if (-not $OutputJson) {
    $outDir = Join-Path $repo 'build/performance'
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $OutputJson = Join-Path $outDir ('baseline-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.json')
}
$source = Join-Path $repo "Shadres/Modification/$ReferencePack/shaders"
$deployed = Join-Path $RunDirectory "shaderpacks/$ShaderPack/shaders"
if (-not (Test-Path -LiteralPath $source -PathType Container)) { throw "Reference shaders missing: $source" }
if (-not (Test-Path -LiteralPath $deployed -PathType Container)) { throw "Deployed shaders missing (unpacked pack required): $deployed" }

function HashFile([string]$Path) {
    if (Test-Path -LiteralPath $Path -PathType Leaf) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }
    return $null
}
function TextHash([string]$Path) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes([IO.File]::ReadAllText($Path).Replace("`r`n", "`n"))
        return [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '')
    } finally { $sha.Dispose() }
}
function GitState([string]$Path) {
    $head = & git -c "safe.directory=$Path" -C $Path rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect repository: $Path" }
    $status = @(& git -c "safe.directory=$Path" -C $Path status --short)
    if ($LASTEXITCODE -ne 0) { throw "Cannot inspect working tree: $Path" }
    return @{ head = "$head"; status = $status }
}

$files = @(foreach ($file in Get-ChildItem -LiteralPath $source -Recurse -File) {
    $relative = $file.FullName.Substring($source.Length).TrimStart('\', '/').Replace('\', '/')
    $target = Join-Path $deployed $relative
    $expected = HashFile $file.FullName
    $actual = HashFile $target
    $status = if (-not $actual) { 'missing' } elseif ($actual -eq $expected) { 'identical' } else { 'different' }
    if ($status -eq 'different' -and $file.Extension -in @('.glsl', '.fsh', '.vsh', '.csh', '.gsh', '.properties', '.lang')) {
        if ((TextHash $file.FullName) -eq (TextHash $target)) { $status = 'eol-only' }
    }
    [pscustomobject]@{ path = $relative; status = $status; referenceSha256 = $expected; deployedSha256 = $actual }
})
$extras = @(foreach ($file in Get-ChildItem -LiteralPath $deployed -Recurse -File) {
    $relative = $file.FullName.Substring($deployed.Length).TrimStart('\', '/').Replace('\', '/')
    if (-not (Test-Path -LiteralPath (Join-Path $source $relative))) { $relative }
})
$irisConfig = Join-Path $RunDirectory 'config/iris.properties'
$configuredPack = $null
if (Test-Path -LiteralPath $irisConfig) {
    $match = Select-String -LiteralPath $irisConfig -Pattern '^shaderPack=(.*)$' | Select-Object -First 1
    if ($match) { $configuredPack = $match.Matches[0].Groups[1].Value }
}
$overrides = Join-Path $RunDirectory "shaderpacks/$ShaderPack.txt"
$suspectMods = @()
$modsDir = Join-Path $RunDirectory 'mods'
if (Test-Path -LiteralPath $modsDir) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $suspectMods = @(foreach ($jar in Get-ChildItem -LiteralPath $modsDir -Filter '*.jar' -File) {
        $zip = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
        try {
            $entry = $zip.GetEntry('fabric.mod.json')
            if ($entry) {
                $reader = [IO.StreamReader]::new($entry.Open())
                try { $mod = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
                if ($mod.id -in @('irlite', 'irl-core')) {
                    [pscustomobject]@{ path = $jar.FullName; id = $mod.id; version = $mod.version; sha256 = (HashFile $jar.FullName) }
                }
            }
        } finally { $zip.Dispose() }
    })
}
$report = [pscustomobject]@{
    schema = 1; capturedAt = (Get-Date).ToString('o')
    addon = (GitState $repo); core = (GitState $core)
    inspectedPack = $ShaderPack; configuredPack = $configuredPack
    runtimeSelectionVerified = $false
    referencePath = $source; deployedPath = $deployed
    irisConfigSha256 = (HashFile $irisConfig); overrideSha256 = (HashFile $overrides)
    overrideText = $(if (Test-Path -LiteralPath $overrides) { [IO.File]::ReadAllText($overrides) } else { $null })
    files = $files; extraDeployedFiles = $extras; competingDevMods = $suspectMods
}
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputJson -Encoding utf8
$files | Group-Object status | Select-Object Name,Count | Format-Table -AutoSize
Write-Output "Disk snapshot: $((Resolve-Path -LiteralPath $OutputJson).Path)"
if ($files.Where({ $_.status -in @('missing', 'different') }).Count -gt 0 -or $extras.Count -gt 0) {
    Write-Warning 'Deployed pack differs from Modification. Review the manifest before comparing performance.'
}
if ($suspectMods.Count -gt 0) { Write-Warning 'run/mods contains IRLite/core jars that may override dev classes; inspect competingDevMods.' }
Write-Output 'Actual runtime identity must be checked separately in profile-session and profile-source log entries.'
