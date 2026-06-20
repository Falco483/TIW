$ErrorActionPreference = "Stop"
try {
    $ppt = New-Object -ComObject PowerPoint.Application
    $ppt.Visible = [Microsoft.Office.Core.MsoTriState]::msoTrue
    $pres = $ppt.Presentations.Open("c:\Users\Antonio\Desktop\progetto TIW\documentation\tabelle_tiw_aggiornate_backup.pptx")
    
    $tableShape = $null
    foreach ($slide in $pres.Slides) {
        foreach ($shape in $slide.Shapes) {
            if ($shape.HasTable -eq -1) {
                $tableShape = $shape
                break
            }
        }
        if ($tableShape) { break }
    }
    
    if ($tableShape) {
        $row = $tableShape.Table.Rows.Item(1)
        $cell = $tableShape.Table.Cell(1,1)
        $tr = $cell.Shape.TextFrame.TextRange
        
        Write-Host "Original row height: $($row.Height)"
        Write-Host "Original cell height: $($cell.Shape.Height)"
        Write-Host "Original BoundHeight: $($tr.BoundHeight)"
        
        $runsCount = $tr.Runs().Count
        for ($i=1; $i -le $runsCount; $i++) {
            $run = $tr.Runs($i)
            $run.Font.Size = [float]($run.Font.Size + 50)
        }
        
        Write-Host "After +50 row height: $($row.Height)"
        Write-Host "After +50 cell height: $($cell.Shape.Height)"
        Write-Host "After +50 BoundHeight: $($tr.BoundHeight)"
    } else {
        Write-Host "No table found"
    }
    
    $pres.Close([Microsoft.Office.Core.MsoTriState]::msoFalse)
    $ppt.Quit()
} catch {
    Write-Host "Error: $_"
    if ($ppt) { $ppt.Quit() }
}
