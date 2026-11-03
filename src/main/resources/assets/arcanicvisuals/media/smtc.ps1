# Arcanic media helper: reads the Windows media session (Spotify, Yandex Music, browsers - anything that shows
# in the volume flyout) and prints one line per update in the launcher's --media format:
#   status \t artist \t title \t position ms \t duration ms \t artwork id
# The cover goes to the artwork path as PNG; "toggle", "next" and "prev" lines in the commands file control it.
param([string]$Artwork, [string]$Commands, [int]$Parent)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    Add-Type -AssemblyName System.Drawing
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
} catch {
    [Console]::Out.WriteLine("arcanic-media-fail winrt " + $_.Exception.Message)
    [Console]::Out.Flush()
    exit 1
}

$asTaskOp = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

function Await($op, [Type]$type) {
    $task = $asTaskOp.MakeGenericMethod($type).Invoke($null, @($op))
    if (-not $task.Wait(4000)) { return $null }
    return $task.Result
}

$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$manager = Await ($managerType::RequestAsync()) $managerType
if ($null -eq $manager) {
    [Console]::Out.WriteLine("arcanic-media-fail manager")
    [Console]::Out.Flush()
    exit 1
}

$lastKey = ''
$coverId = ''
$coverTries = 0
$commandsRead = 0
$tick = 0

function Clean([string]$s) {
    if ($null -eq $s) { return '' }
    return ($s -replace '[\t\r\n]', ' ').Trim()
}

function Save-Cover($props, [string]$path) {
    $ref = $props.Thumbnail
    if ($null -eq $ref) { return $false }
    $stream = Await ($ref.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
    if ($null -eq $stream) { return $false }
    try {
        $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
        $memory = New-Object System.IO.MemoryStream
        $net.CopyTo($memory)
        $memory.Position = 0
        $image = [System.Drawing.Image]::FromStream($memory)
        $tmp = $path + '.tmp'
        $image.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
        $image.Dispose()
        $memory.Dispose()
        Move-Item -LiteralPath $tmp -Destination $path -Force
        return $true
    } catch {
        return $false
    } finally {
        $stream.Dispose()
    }
}

while ($true) {
    if ($Parent -gt 0 -and $null -eq (Get-Process -Id $Parent -ErrorAction SilentlyContinue)) { exit 0 }

    # the session that plays wins over the one Windows calls current (a paused tab must not hide Spotify)
    $session = $null
    try {
        $session = $manager.GetCurrentSession()
        foreach ($s in $manager.GetSessions()) {
            if ([int]$s.GetPlaybackInfo().PlaybackStatus -eq 4) { $session = $s; break }
        }
    } catch { }

    # commands written by the game: play/pause, next, previous
    if ($Commands -and (Test-Path -LiteralPath $Commands)) {
        try {
            $lines = @(Get-Content -LiteralPath $Commands -ErrorAction SilentlyContinue)
            for ($i = $commandsRead; $i -lt $lines.Count; $i++) {
                if ($null -eq $session) { continue }
                switch ($lines[$i].Trim()) {
                    'toggle' { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
                    'next' { $null = Await ($session.TrySkipNextAsync()) ([bool]) }
                    'prev' { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }
                }
            }
            $commandsRead = $lines.Count
        } catch { }
    }

    if ($null -eq $session) {
        [Console]::Out.WriteLine("none`t`t`t0`t0`t")
    } else {
        try {
            $props = Await ($session.TryGetMediaPropertiesAsync()) $propsType
            $info = $session.GetPlaybackInfo()
            $timeline = $session.GetTimelineProperties()
            $status = switch ([int]$info.PlaybackStatus) { 4 { 'playing' } 5 { 'paused' } default { 'none' } }
            $artist = Clean $props.Artist
            if ($artist -eq '') { $artist = Clean $props.AlbumArtist }
            $title = Clean $props.Title
            # a browser tab without metadata reports only "<browser> is playing media": not a track
            if ($artist -eq '' -and $title -match '(is playing media|\u0432\u043e\u0441\u043f\u0440\u043e\u0438\u0437\u0432\u043e\u0434\u0438\u0442 \u043c\u0435\u0434\u0438\u0430)$') { $status = 'none' }
            $duration = [long]($timeline.EndTime - $timeline.StartTime).TotalMilliseconds
            $position = [long]$timeline.Position.TotalMilliseconds
            if ($status -eq 'playing' -and $timeline.LastUpdatedTime.Ticks -gt 0) {
                $since = ([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalMilliseconds
                if ($since -gt 0 -and $since -lt 600000) { $position += [long]$since }
            }
            if ($duration -gt 0) { $position = [Math]::Max(0, [Math]::Min($position, $duration)) } else { $position = 0 }
            $key = $artist + '|' + $title
            if ($key -ne $lastKey) {
                $lastKey = $key
                $coverId = ''
                $coverTries = 0
            }
            # players often post the thumbnail a moment after the title: try a few times while there is none;
            # a cover that cannot be read must never cost the line itself
            if ($coverId -eq '' -and $coverTries -lt 8 -and ($coverTries -eq 0 -or $tick % 3 -eq 0)) {
                $coverTries++
                try {
                    if ($Artwork -and (Save-Cover $props $Artwork)) { $coverId = [string]([Math]::Abs($key.GetHashCode())) }
                } catch { $coverId = '' }
            }
            [Console]::Out.WriteLine("$status`t$artist`t$title`t$position`t$duration`t$coverId")
        } catch {
            [Console]::Out.WriteLine("none`t`t`t0`t0`t")
        }
    }
    [Console]::Out.Flush()
    $tick++
    Start-Sleep -Milliseconds 400
}
