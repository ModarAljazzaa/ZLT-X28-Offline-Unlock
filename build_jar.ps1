$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

$classes = Join-Path $PSScriptRoot "build-java\classes"
$output = Join-Path $PSScriptRoot "dist\ZLT-X28-Unlock.jar"

if (Test-Path -LiteralPath $classes) {
    Remove-Item -LiteralPath $classes -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $classes | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path $output) | Out-Null

javac --release 8 -encoding UTF-8 -d $classes ".\src\ZltX28Unlock.java"
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed with exit code $LASTEXITCODE." }

Copy-Item -LiteralPath ".\x28.tgz" -Destination (Join-Path $classes "x28.tgz") -Force
jar cfe $output ZltX28Unlock -C $classes .
if ($LASTEXITCODE -ne 0) { throw "JAR packaging failed with exit code $LASTEXITCODE." }

Write-Host "Executable JAR created at: $output"
