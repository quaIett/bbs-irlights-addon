param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleUserHome = $env:GRADLE_USER_HOME,
    [string]$Baseline,
    [string]$Current,
    [string]$OutputDirectory,
    [ValidateRange(16, 1920)][int]$Width = 256,
    [ValidateRange(16, 1080)][int]$Height = 144,
    [ValidateRange(1, 2000)][int]$WarmupPairs = 40,
    [ValidateRange(5, 2000)][int]$TimingPairs = 80,
    [switch]$IsolateChanges
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$workspace = Split-Path -Parent (Split-Path -Parent $repo)
if (!$JavaHome -and (Test-Path -LiteralPath 'C:/Program Files/Java/jdk-21')) { $JavaHome = 'C:/Program Files/Java/jdk-21' }
if (!$GradleUserHome) { $GradleUserHome = Join-Path $workspace '.gradle-user' }
if (!$Baseline) { $Baseline = Join-Path $PSScriptRoot 'vl/reference/irlite_lights.glsl' }
if (!$Current) { $Current = Join-Path $repo 'Shadres/Modification/ComplementaryReimagined/shaders/lib/irlite/irlite_lights.glsl' }
if (!$OutputDirectory) { $OutputDirectory = Join-Path $repo 'build/vl-gpu' }
$common = Join-Path $repo 'Shadres/Modification/ComplementaryReimagined/shaders/lib/util/commonFunctions.glsl'
$classes = Join-Path $OutputDirectory 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$jars = [Collections.Generic.List[string]]::new()
foreach ($module in @('lwjgl', 'lwjgl-glfw', 'lwjgl-opengl')) {
    $modulePath = Join-Path $GradleUserHome "caches/modules-2/files-2.1/org.lwjgl/$module/3.3.2"
    foreach ($filename in @("$module-3.3.2.jar", "$module-3.3.2-natives-windows.jar")) {
        $found = @(Get-ChildItem -LiteralPath $modulePath -Recurse -Filter $filename)
        if ($found.Count -ne 1) { throw "Expected one cached $filename under $modulePath" }
        $jars.Add($found[0].FullName)
    }
}
$classpath = $jars -join [IO.Path]::PathSeparator
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
& $javac --release 17 -proc:none -encoding UTF-8 -cp $classpath -d $classes (Join-Path $PSScriptRoot 'vl/VlGpuTest.java')
if ($LASTEXITCODE -ne 0) { throw 'VL GPU harness compilation failed' }
$runArgs = @('-Djava.awt.headless=true', '-cp', "$classes$([IO.Path]::PathSeparator)$classpath", 'VlGpuTest',
    $Baseline, $Current, $common, $OutputDirectory, "$Width", "$Height", "$WarmupPairs", "$TimingPairs")
if ($IsolateChanges) { $runArgs += 'isolate' }
& $java @runArgs
if ($LASTEXITCODE -ne 0) { throw 'VL GPU validation failed; inspect generated GLSL, compile logs and report.json in the output directory' }
