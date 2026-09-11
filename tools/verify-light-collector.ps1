param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleUserHome = $env:GRADLE_USER_HOME,
    [string]$ClientClasses
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$workspace = Split-Path -Parent (Split-Path -Parent $repo)
if (!$GradleUserHome) { $GradleUserHome = Join-Path $workspace '.gradle-user' }
if (!$ClientClasses) { $ClientClasses = Join-Path $repo 'build/classes/java/client' }
$nestedClass = Join-Path $ClientClasses 'qualet/irlite/client/light/LightCollector$TraversalScratch.class'
if (!(Test-Path -LiteralPath $nestedClass)) {
    throw 'Run the addon compileClientJava task first, or supply -ClientClasses with its compiled output.'
}
$jomlDir = Join-Path $GradleUserHome 'caches/modules-2/files-2.1/org.joml/joml'
$joml = Get-ChildItem -LiteralPath $jomlDir -Recurse -Filter 'joml-*.jar' |
    Where-Object Name -NotMatch '(sources|javadoc)' | Sort-Object FullName | Select-Object -First 1
if (!$joml) { throw "JOML JAR not found under $jomlDir" }
$classes = Join-Path $repo 'build/light-collector-test'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$classpath = "$ClientClasses$([IO.Path]::PathSeparator)$($joml.FullName)"
& $javac --release 17 -encoding UTF-8 -cp $classpath -d $classes (Join-Path $PSScriptRoot 'light-collector/LightCollectorMathTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Light collector test compilation failed' }
& $java -cp "$classes$([IO.Path]::PathSeparator)$classpath" qualet.irlite.client.light.LightCollectorMathTest
if ($LASTEXITCODE -ne 0) { throw 'Light collector math tests failed' }
