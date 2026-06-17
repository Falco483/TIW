$ErrorActionPreference = "Stop"

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
                Write-Host "Setting font size 13 for table on slide $($slide.SlideIndex)"
                for ($r=1; $r -le $table.Rows.Count; $r++) {
                    for ($c=1; $c -le $table.Columns.Count; $c++) {
                        $cell = $table.Cell($r, $c)
                        if ($cell.Shape.HasTextFrame) {
                            $tr = $cell.Shape.TextFrame.TextRange
                            if (-not [string]::IsNullOrWhiteSpace($tr.Text)) {
                                $runsCount = $tr.Runs().Count
                                for ($i=1; $i -le $runsCount; $i++) {
                                    $run = $tr.Runs($i)
                                    try {
                                        $run.Font.Size = 13
                                    } catch { }
                                }
                            }
                        }
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
