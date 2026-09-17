<#
.SYNOPSIS
  One-command GitHub release for irlite (bbs-irlights-addon).

  The product jar links against a third-party BBS jar that is NOT in git
  (libs/*.jar is .gitignore'd and cannot be republished), so the build can only
  run on a dev machine. This script does the whole release locally:

    1. Validate: gh present & authenticated, clean tree (unless -AllowDirty),
       version well-formed, tag v<Version> not already taken.
    2. Bump mod_version in gradle.properties if it differs, and commit that.
    3. gradlew clean build -Pmc=<Mc>  (Mc defaults to the 1.20.1 product target).
    4. Push the branch, then `gh release create v<Version> <jar> --generate-notes`
       which creates the remote tag + Release and uploads the jar in one call.

  The matching .github/workflows/release.yml is only a safety net for tags that
  get pushed by hand without a Release; the normal path is this script.

.EXAMPLE
  pwsh -File tools\release.ps1 -Version 1.1.2

.EXAMPLE
  # Preview every step without pushing, building, or publishing anything:
  pwsh -File tools\release.ps1 -Version 1.1.2 -DryRun

.EXAMPLE
  # Cut a draft (review on GitHub, then publish by hand):
  pwsh -File tools\release.ps1 -Version 1.1.2 -Draft
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Mc = '1.20.1',
    [switch]$Draft,
    [switch]$AllowDirty,
    [switch]$SkipBuild,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'

# Run from the repo root regardless of where the script was invoked from.
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Step([string]$msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

# --- 1. Validate --------------------------------------------------------------
if ($Version -notmatch '^\d+\.\d+(\.\d+)?$') {
    throw "Version '$Version' must look like 1.1 or 1.1.2 (no leading 'v')."
}
$tag = "v$Version"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI 'gh' not found on PATH. Install from https://cli.github.com/"
}
gh auth status 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) { throw "gh is not authenticated. Run: gh auth login" }

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
if ($branch -eq 'HEAD') { throw "Detached HEAD - checkout a branch before releasing." }

$dirty = git status --porcelain
if ($dirty -and -not $AllowDirty) {
    throw "Working tree not clean. Commit/stash first, or pass -AllowDirty.`n$dirty"
}

if (git tag --list $tag) { throw "Tag $tag already exists locally." }
git ls-remote --exit-code --tags origin $tag *> $null
if ($LASTEXITCODE -eq 0) { throw "Tag $tag already exists on origin." }

Write-Host ""
Step "Releasing irlite $tag  (branch: $branch, MC target: $Mc)"
if ($DryRun) { Write-Host "    -- DRY RUN: nothing will be built, pushed, or published --" -ForegroundColor Yellow }
Write-Host ""

# --- 2. Bump mod_version (commit only if it changed) --------------------------
$propsPath = Join-Path $repoRoot 'gradle.properties'
$props     = Get-Content $propsPath -Raw
$current   = [regex]::Match($props, '(?m)^mod_version=(.+)$').Groups[1].Value.Trim()
if ($current -ne $Version) {
    Step "Bumping mod_version: $current -> $Version"
    if (-not $DryRun) {
        $props = [regex]::Replace($props, '(?m)^mod_version=.+$', "mod_version=$Version")
        # UTF-8 without BOM: a BOM would corrupt the first gradle.properties key.
        [System.IO.File]::WriteAllText($propsPath, $props, (New-Object System.Text.UTF8Encoding($false)))
        git add gradle.properties
        git commit -m "release: $tag" | Out-Null
    }
} else {
    Write-Host "    mod_version already $Version, no bump needed."
}

# --- 3. Build the product jar -------------------------------------------------
$jarName = "irlite-$Version+mc$Mc.jar"
$jarPath = Join-Path $repoRoot "build\libs\$jarName"
if ($SkipBuild) {
    Step "Skipping build (-SkipBuild); expecting $jarName to already exist"
} else {
    Step "Building: gradlew clean build -Pmc=$Mc"
    if (-not $DryRun) {
        & "$repoRoot\gradlew.bat" clean build "-Pmc=$Mc"
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)." }
    }
}
if (-not $DryRun -and -not (Test-Path $jarPath)) {
    throw "Expected artifact not found: $jarPath`nDid the build produce it? (check build\libs\)"
}

# --- 4. Push branch, then create the tag + Release + upload the jar -----------
Step "Pushing $branch to origin"
if (-not $DryRun) {
    git push origin $branch
    if ($LASTEXITCODE -ne 0) { throw "git push failed." }
}

$ghArgs = @('release', 'create', $tag, $jarPath,
            '--title', $tag, '--target', $branch, '--generate-notes')
if ($Draft) { $ghArgs += '--draft' }

Step "Creating GitHub Release $tag with $jarName attached"
if ($DryRun) {
    Write-Host "    [dry-run] gh $($ghArgs -join ' ')" -ForegroundColor Yellow
    Write-Host "`nDry run complete. Re-run without -DryRun to publish." -ForegroundColor Green
    exit 0
}
gh @ghArgs
if ($LASTEXITCODE -ne 0) { throw "gh release create failed." }

Write-Host ""
Step "Done - $tag published"
gh release view $tag --web
exit 0
