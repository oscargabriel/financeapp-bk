param([Parameter(Mandatory)][int]$Pr)

# Solo mergea PRs cuya base sea dev: main es produccion y su merge es del usuario.
$ErrorActionPreference = 'Stop'

$info = gh pr view $Pr --json baseRefName,state | ConvertFrom-Json
if ($info.baseRefName -ne 'dev') {
    Write-Error "El PR #$Pr apunta a '$($info.baseRefName)', no a dev. No se mergea."
    exit 1
}
if ($info.state -ne 'OPEN') {
    Write-Error "El PR #$Pr esta en estado $($info.state)."
    exit 1
}

gh pr merge $Pr --merge
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Cambios locales ajenos a la entrega pueden impedir el checkout: se apartan y se reponen.
$sucio = git status --porcelain --untracked-files=no
if ($sucio) { git stash push -m "merge-pr-dev-$Pr" | Out-Null }
git checkout dev
git pull --ff-only
if ($sucio) { git stash pop }
git log --oneline -1
