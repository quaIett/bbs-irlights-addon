param(
    [string]$AddonJar,
    [string]$CaptureMetadata,
    [string]$OutputJson
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = Split-Path -Parent $PSScriptRoot
if (!$AddonJar) {
    $AddonJar = (Get-ChildItem -LiteralPath (Join-Path $repo 'build/libs') -Filter 'irlite-*+mc1.20.4.jar' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (!$AddonJar) { throw 'No built 1.20.4 addon JAR' }
function Digest([byte[]]$bytes) { [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)) }
function EntryBytes($entry) {
    $stream = $entry.Open()
    $buffer = [IO.MemoryStream]::new()
    try { $stream.CopyTo($buffer); return ,$buffer.ToArray() }
    finally { $stream.Dispose(); $buffer.Dispose() }
}
$zip = [IO.Compression.ZipFile]::OpenRead($AddonJar)
try {
    $cores = @($zip.Entries | Where-Object { $_.FullName -match '^META-INF/jars/irl-core.*\.jar$' })
    if ($cores.Count -ne 1) { throw "Expected exactly one nested core, found $($cores.Count)" }
    $coreBytes = EntryBytes $cores[0]
    $memory = [IO.MemoryStream]::new($coreBytes, $false)
    $nested = [IO.Compression.ZipArchive]::new($memory, [IO.Compression.ZipArchiveMode]::Read)
    try {
        $metadata = [Text.Encoding]::UTF8.GetString((EntryBytes $nested.GetEntry('fabric.mod.json'))) | ConvertFrom-Json
        $coreJar = Join-Path (Split-Path -Parent $repo) "irl-core/build/libs/irl-core-$($metadata.version).jar"
        if ((Digest $coreBytes) -ne (Get-FileHash -LiteralPath $coreJar -Algorithm SHA256).Hash) { throw 'Nested core is stale' }
        $class = EntryBytes $nested.GetEntry('org/qualet/irl/light/shadow/ShadowBaker.class')
        $major = ([int]$class[6] -shl 8) -bor $class[7]
        if ($major -ne 61) { throw "Expected Java 17 bytecode, got $major" }
        $result = [ordered]@{
            addon = (Resolve-Path -LiteralPath $AddonJar).Path
            addonSha256 = (Get-FileHash -LiteralPath $AddonJar -Algorithm SHA256).Hash
            nestedCore = $cores[0].FullName
            coreVersion = $metadata.version
            coreSha256 = Digest $coreBytes
            javaClassMajor = $major
        }
    } finally { $nested.Dispose(); $memory.Dispose() }
} finally { $zip.Dispose() }
if ($CaptureMetadata) {
    $capture = Get-Content -LiteralPath $CaptureMetadata -Raw | ConvertFrom-Json
    if ($capture.coreVersion -ne $result.coreVersion) { throw 'Runtime core version mismatch' }
    $resource = $capture.coreClassResource
    $namedJar = [Uri]::new($resource.Substring(4, $resource.IndexOf('!') - 4)).LocalPath
    $named = [IO.Compression.ZipFile]::OpenRead($namedJar)
    try { $runtimeHash = Digest (EntryBytes $named.GetEntry('org/qualet/irl/light/shadow/ShadowBaker.class')) }
    finally { $named.Dispose() }
    if ($runtimeHash -ne $capture.coreClassSha256) { throw 'Runtime class differs from the named dev JAR' }
    $result.runtimeCoreClassSha256 = $runtimeHash
    $result.runtimeMatchesNamedJar = $true
}
if (!$OutputJson) { $OutputJson = Join-Path $repo 'build/performance/stage1a-build-verification.json' }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputJson) | Out-Null
$result | ConvertTo-Json | Set-Content -LiteralPath $OutputJson -Encoding utf8
[pscustomobject]$result
