param([string]$Path, [string]$OutDir)

$script:id = 0
function NewId { $script:id++; return "t$($script:id)" }

function Inline([string]$text) {
    $nodes = @()
    $parts = [regex]::Split($text, '(`[^`]+`)')
    foreach ($p in $parts) {
        if ($p -eq '') { continue }
        if ($p -match '^`([^`]+)`$') {
            $nodes += [ordered]@{ type = 'text'; text = $Matches[1]; marks = @(@{ type = 'code' }) }
            continue
        }
        $bparts = [regex]::Split($p, '(\*\*[^*]+\*\*)')
        foreach ($b in $bparts) {
            if ($b -eq '') { continue }
            $bold = $false
            if ($b -match '^\*\*([^*]+)\*\*$') { $b = $Matches[1]; $bold = $true }
            $uparts = [regex]::Split($b, '(https?://[^\s)]+)')
            foreach ($u in $uparts) {
                if ($u -eq '') { continue }
                $marks = @()
                if ($bold) { $marks += @{ type = 'strong' } }
                if ($u -match '^https?://') { $marks += @{ type = 'link'; attrs = @{ href = $u } } }
                $n = [ordered]@{ type = 'text'; text = $u }
                if ($marks.Count) { $n.marks = $marks }
                $nodes += $n
            }
        }
    }
    return , $nodes
}

function Para([string]$t) { [ordered]@{ type = 'paragraph'; content = (Inline $t) } }

function Convert([string[]]$lines) {
    $content = @()
    $i = 0
    while ($i -lt $lines.Count) {
        $l = $lines[$i]
        if ($l.Trim() -eq '') { $i++; continue }
        if ($l -match '^## (.*)$') {
            $content += [ordered]@{ type = 'heading'; attrs = @{ level = 2 }; content = (Inline $Matches[1]) }; $i++; continue
        }
        if ($l -match '^> ?') {
            $q = @()
            while ($i -lt $lines.Count -and $lines[$i] -match '^> ?(.*)$') { if ($Matches[1].Trim()) { $q += Para $Matches[1] }; $i++ }
            $content += [ordered]@{ type = 'blockquote'; content = $q }; continue
        }
        if ($l -match '^- \[( |x)\] ') {
            $items = @()
            while ($i -lt $lines.Count -and $lines[$i] -match '^- \[( |x)\] (.*)$') {
                $state = if ($Matches[1] -eq 'x') { 'DONE' } else { 'TODO' }
                $items += [ordered]@{ type = 'taskItem'; attrs = @{ localId = (NewId); state = $state }; content = (Inline $Matches[2]) }
                $i++
            }
            $content += [ordered]@{ type = 'taskList'; attrs = @{ localId = (NewId) }; content = $items }; continue
        }
        if ($l -match '^- ') {
            $items = @()
            while ($i -lt $lines.Count -and $lines[$i] -match '^- (.*)$' -and $lines[$i] -notmatch '^- \[( |x)\] ') {
                $items += [ordered]@{ type = 'listItem'; content = @(Para $Matches[1]) }; $i++
            }
            $content += [ordered]@{ type = 'bulletList'; content = $items }; continue
        }
        $content += Para $l; $i++
    }
    return [ordered]@{ type = 'doc'; version = 1; content = $content }
}

$raw = Get-Content -Raw -Encoding utf8 $Path
$blocks = [regex]::Split($raw, '(?m)^=== ')
New-Item -ItemType Directory -Force $OutDir | Out-Null
foreach ($b in $blocks) {
    if ($b.Trim() -eq '') { continue }
    $nl = $b.IndexOf("`n")
    $key = $b.Substring(0, $nl).Trim()
    $body = $b.Substring($nl + 1) -split "`r?`n"
    $script:id = 0
    $doc = Convert $body
    $doc | ConvertTo-Json -Depth 30 -Compress | Set-Content -Encoding utf8 (Join-Path $OutDir "$key.json")
}
Get-ChildItem $OutDir -Name
