Write-Host "Cleaning up Android project build artifacts to avoid path length issues..." -ForegroundColor Green

# Function to safely remove directories with long paths
function Remove-LongPath {
    param([string]$Path)
    if (Test-Path $Path) {
        Write-Host "Removing $Path..." -ForegroundColor Yellow
        try {
            # Use robocopy to create an empty directory and mirror it to delete contents
            $emptyDir = Join-Path $env:TEMP "empty_$(Get-Random)"
            New-Item -ItemType Directory -Path $emptyDir -Force | Out-Null
            robocopy $emptyDir $Path /MIR /NP /NJH /NJS | Out-Null
            Remove-Item $emptyDir -Force
            Remove-Item $Path -Force
            Write-Host "Successfully removed $Path" -ForegroundColor Green
        }
        catch {
            Write-Host "Warning: Could not remove $Path - $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# Remove problematic directories
Remove-LongPath "build"
Remove-LongPath "app\build"
Remove-LongPath ".gradle"
Remove-LongPath ".idea"
Remove-LongPath ".kotlin"

Write-Host ""
Write-Host "Cleanup completed! The following directories have been removed:" -ForegroundColor Green
Write-Host "- build/ (root build artifacts)" -ForegroundColor Cyan
Write-Host "- app/build/ (app build artifacts)" -ForegroundColor Cyan
Write-Host "- .gradle/ (Gradle cache)" -ForegroundColor Cyan
Write-Host "- .idea/ (IDE files)" -ForegroundColor Cyan
Write-Host "- .kotlin/ (Kotlin cache)" -ForegroundColor Cyan
Write-Host ""
Write-Host "Your project can now be safely zipped without path length issues." -ForegroundColor Green
Write-Host "To rebuild the project, simply open it in Android Studio or run './gradlew build'" -ForegroundColor Yellow
Write-Host ""
Read-Host "Press Enter to continue"
