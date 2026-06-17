$ErrorActionPreference = "Stop"

function Optimize-TableCellText {
    param($cell)
    
    if (-not $cell.Shape.HasTextFrame) { return }
    $tr = $cell.Shape.TextFrame.TextRange
    if ([string]::IsNullOrWhiteSpace($tr.Text)) { return }
    
    $maxHeight = $cell.Shape.Height - $cell.Shape.TextFrame.MarginTop - $cell.Shape.TextFrame.MarginBottom
    $maxWidth = $cell.Shape.Width - $cell.Shape.TextFrame.MarginLeft - $cell.Shape.TextFrame.MarginRight
    
    $runsCount = $tr.Runs().Count
    $origSizes = @()
    for ($i=1; $i -le $runsCount; $i++) {
        $run = $tr.Runs($i)
        $fSize = -1
        try {
            $fSize = $run.Font.Size
        } catch { }
        $origSizes += $fSize
    }
    
    $increment = 1
    $maxIncrement = 0
    $overflow = $false
    
    while (-not $overflow) {
        $currentIncrement = $maxIncrement + $increment
        
        for ($i=1; $i -le $runsCount; $i++) {
            if ($origSizes[$i-1] -gt 0) {
                $run = $tr.Runs($i)
                $newSize = $origSizes[$i-1] + $currentIncrement
                try {
                    $run.Font.Size = [float]$newSize
                } catch { }
            }
        }
        
        if ($tr.BoundHeight -gt $maxHeight -or $tr.BoundWidth -gt $maxWidth) {
            $overflow = $true
        } else {
            $maxIncrement = $currentIncrement
        }
        
        if ($currentIncrement -gt 100) { break }
    }
    
    for ($i=1; $i -le $runsCount; $i++) {
        if ($origSizes[$i-1] -gt 0) {
            $run = $tr.Runs($i)
            try {
                $run.Font.Size = [float]($origSizes[$i-1] + $maxIncrement)
            } catch { }
        }
    }
}

try {
    Write-Host "Starting PPT..."
    $ppt = New-Object -ComObject PowerPoint.Application
    
    $path = "c:\Users\Antonio\Desktop\progetto TIW\documentation\tabelle_tiw_aggiornate.pptx"
    Write-Host "Opening Presentation: $path"
    $pres = $ppt.Presentations.Open($path)
    
    foreach ($slide in $pres.Slides) {
        foreach ($shape in $slide.Shapes) {
            if ($shape.HasTable -eq -1) {
                $table = $shape.Table
                Write-Host "Optimizing table on slide $($slide.SlideIndex)"
                for ($r=1; $r -le $table.Rows.Count; $r++) {
                    for ($c=1; $c -le $table.Columns.Count; $c++) {
                        $cell = $table.Cell($r, $c)
                        Optimize-TableCellText -cell $cell
                    }
                }
            }
        }
    }
    
    Write-Host "Saving presentation..."
    $pres.Save()
    $pres.Close()
    $ppt.Quit()
    Write-Host "Done."
} catch {
    Write-Host "Error: $_"
    if ($pres) { try { $pres.Close() } catch {} }
    if ($ppt) { try { $ppt.Quit() } catch {} }
}
