Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$assetDir = Join-Path $root "playstore\assets"

function New-Canvas($width, $height) {
    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    return @($bitmap, $graphics)
}

function Brush($hex) {
    return New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml($hex))
}

function Pen($hex, $width) {
    $pen = New-Object System.Drawing.Pen([System.Drawing.ColorTranslator]::FromHtml($hex), $width)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    return $pen
}

function RoundRect($x, $y, $w, $h, $r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function Draw-BrandMark($graphics, $scale, $offsetX, $offsetY) {
    $white = Brush "#FFFFFF"
    $mint = Brush "#DFF5DC"
    $lime = Brush "#89C64A"
    $olive = Brush "#274C2E"
    $sun = Brush "#F6C65B"

    $graphics.FillEllipse($sun, $offsetX + 360 * $scale, $offsetY + 70 * $scale, 90 * $scale, 90 * $scale)
    $graphics.FillPath($white, (RoundRect ($offsetX + 115 * $scale) ($offsetY + 240 * $scale) (230 * $scale) (155 * $scale) (32 * $scale)))

    $leaf1 = New-Object System.Drawing.Drawing2D.GraphicsPath
    $leaf1.AddBezier($offsetX + 160 * $scale, $offsetY + 210 * $scale, $offsetX + 110 * $scale, $offsetY + 120 * $scale, $offsetX + 150 * $scale, $offsetY + 70 * $scale, $offsetX + 205 * $scale, $offsetY + 55 * $scale)
    $leaf1.AddBezier($offsetX + 205 * $scale, $offsetY + 55 * $scale, $offsetX + 245 * $scale, $offsetY + 125 * $scale, $offsetX + 245 * $scale, $offsetY + 180 * $scale, $offsetX + 210 * $scale, $offsetY + 225 * $scale)
    $leaf1.CloseFigure()
    $graphics.FillPath($mint, $leaf1)

    $leaf2 = New-Object System.Drawing.Drawing2D.GraphicsPath
    $leaf2.AddBezier($offsetX + 220 * $scale, $offsetY + 215 * $scale, $offsetX + 280 * $scale, $offsetY + 145 * $scale, $offsetX + 345 * $scale, $offsetY + 120 * $scale, $offsetX + 430 * $scale, $offsetY + 155 * $scale)
    $leaf2.AddBezier($offsetX + 430 * $scale, $offsetY + 155 * $scale, $offsetX + 405 * $scale, $offsetY + 250 * $scale, $offsetX + 315 * $scale, $offsetY + 285 * $scale, $offsetX + 230 * $scale, $offsetY + 270 * $scale)
    $leaf2.CloseFigure()
    $graphics.FillPath($mint, $leaf2)

    $graphics.FillEllipse($lime, $offsetX + 170 * $scale, $offsetY + 95 * $scale, 70 * $scale, 110 * $scale)
    $graphics.FillEllipse($lime, $offsetX + 275 * $scale, $offsetY + 160 * $scale, 120 * $scale, 70 * $scale)

    $graphics.FillRectangle($olive, $offsetX + 155 * $scale, $offsetY + 280 * $scale, 58 * $scale, 84 * $scale)
    $graphics.FillRectangle($olive, $offsetX + 235 * $scale, $offsetY + 280 * $scale, 58 * $scale, 84 * $scale)
    $graphics.FillEllipse($sun, $offsetX + 174 * $scale, $offsetY + 310 * $scale, 40 * $scale, 40 * $scale)
    $graphics.FillEllipse($sun, $offsetX + 254 * $scale, $offsetY + 325 * $scale, 40 * $scale, 40 * $scale)
    $graphics.DrawBezier((Pen "#274C2E" (24 * $scale)), $offsetX + 185 * $scale, $offsetY + 390 * $scale, $offsetX + 200 * $scale, $offsetY + 300 * $scale, $offsetX + 240 * $scale, $offsetY + 235 * $scale, $offsetX + 305 * $scale, $offsetY + 195 * $scale)
}

$icon = New-Canvas 512 512
$bitmap = $icon[0]
$graphics = $icon[1]
$graphics.FillPath((Brush "#274C2E"), (RoundRect 0 0 512 512 104))
$graphics.FillPie((Brush "#315B38"), -60, 300, 620, 260, 180, 180)
$graphics.FillPie((Brush "#89C64A"), -80, 392, 660, 210, 180, 180)
Draw-BrandMark $graphics 1 0 0
$bitmap.Save((Join-Path $assetDir "icon_512.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$bitmap.Dispose()

$feature = New-Canvas 1024 500
$bitmap = $feature[0]
$graphics = $feature[1]
$graphics.Clear([System.Drawing.ColorTranslator]::FromHtml("#274C2E"))
$graphics.FillPie((Brush "#315B38"), -120, 260, 1260, 420, 180, 180)
$graphics.FillPie((Brush "#89C64A"), -140, 382, 1300, 260, 180, 180)
Draw-BrandMark $graphics 0.62 20 70

$titleFont = New-Object System.Drawing.Font("Arial", 76, [System.Drawing.FontStyle]::Bold)
$subtitleFont = New-Object System.Drawing.Font("Arial", 34, [System.Drawing.FontStyle]::Regular)
$smallFont = New-Object System.Drawing.Font("Arial", 28, [System.Drawing.FontStyle]::Regular)
$graphics.DrawString("HuertoCero", $titleFont, (Brush "#FFFFFF"), 420, 145)
$graphics.DrawString("Productos locales, reservas y chat directo", $subtitleFont, (Brush "#DFF5DC"), 424, 250)
$graphics.DrawString("Compra y vende desde el huerto", $smallFont, (Brush "#FFFFFF"), 424, 318)
$bitmap.Save((Join-Path $assetDir "feature_graphic.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$titleFont.Dispose()
$subtitleFont.Dispose()
$smallFont.Dispose()
$graphics.Dispose()
$bitmap.Dispose()
