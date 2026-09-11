param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleUserHome = $env:GRADLE_USER_HOME,
    [string]$ClientClasses
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (!$GradleUserHome) { $GradleUserHome = Join-Path $env:USERPROFILE '.gradle' }
if (!$ClientClasses) { $ClientClasses = Join-Path $repo 'build/classes/java/client' }
$signature = Join-Path $ClientClasses 'qualet/irlite/client/light/BbsModelSilhouette$Signature.class'
if (!(Test-Path -LiteralPath $signature)) { throw 'Compile the addon client classes first.' }
$joml = Get-ChildItem -LiteralPath (Join-Path $GradleUserHome 'caches/modules-2/files-2.1/org.joml/joml/1.10.5') -Recurse -Filter 'joml-1.10.5.jar' | Select-Object -First 1
$mcRoot = Join-Path $GradleUserHome 'caches/fabric-loom/minecraftMaven/net/minecraft'
$mc = Get-ChildItem -LiteralPath (Join-Path $mcRoot 'minecraft-clientonly') -Recurse -Filter '*1.20.4*build.1-v2.jar' | Select-Object -First 1
$common = Get-ChildItem -LiteralPath (Join-Path $mcRoot 'minecraft-common') -Recurse -Filter '*1.20.4*build.1-v2.jar' | Select-Object -First 1
$fabric = Get-ChildItem -LiteralPath (Join-Path $GradleUserHome 'caches/modules-2/files-2.1/net.fabricmc/fabric-loader') -Recurse -Filter 'fabric-loader-*.jar' |
    Where-Object Name -NotMatch '(sources|javadoc)' | Select-Object -First 1
if (!$joml -or !$mc -or !$common -or !$fabric) { throw 'Missing cached JOML 1.10.5, Fabric loader or named Minecraft 1.20.4 jars.' }
$classes = Join-Path $repo 'build/bbs-pose-scratch-test'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
# Actual audited BBS bytecode, actual JOML, actual vanilla ModelPart: no stubs.
$classpath = @($ClientClasses, (Join-Path $repo 'libs/bbs-2.3.1-1.20.4.jar'), $joml.FullName, $mc.FullName, $common.FullName, $fabric.FullName) -join [IO.Path]::PathSeparator
$sources = @(
    (Join-Path $repo 'src/client/java/qualet/irlite/client/light/BbsModelPoseScratch.java'),
    (Join-Path $repo 'src/client/java/qualet/irlite/client/light/BbsMobPoseScratch.java'),
    (Join-Path $PSScriptRoot 'bbs-pose-scratch/BbsPoseScratchTest.java')
)
& $javac --release 17 -encoding UTF-8 -cp $classpath -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'BBS pose scratch test compilation failed' }
& $java -cp "$classes$([IO.Path]::PathSeparator)$classpath" qualet.irlite.client.light.BbsPoseScratchTest
if ($LASTEXITCODE -ne 0) { throw 'BBS pose scratch tests failed' }
