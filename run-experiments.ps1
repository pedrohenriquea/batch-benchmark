<#
.SYNOPSIS
    Executa os cenarios experimentais (carga -> warm-up -> repeticoes) e exporta os
    resultados quantitativos para results/results.csv.

.EXAMPLE
    ./run-experiments.ps1
    ./run-experiments.ps1 -Volumes 100000,500000 -Strategies jpa,native -Reps 10
#>
param(
    [string]   $BaseUrl    = "http://localhost:8080",
    [int[]]    $Volumes    = @(100000, 500000, 1000000),
    [string[]] $Strategies = @("jpa", "native", "native-bulk"),
    [int]      $Reps       = 5,
    [int]      $ChunkSize  = 1000,
    [string]   $OutFile    = "results/results.csv"
)

$ErrorActionPreference = "Stop"

# Forca ponto como separador decimal (evita virgula de culturas como pt-BR, que quebraria o CSV).
[System.Threading.Thread]::CurrentThread.CurrentCulture = [System.Globalization.CultureInfo]::InvariantCulture

# Aguarda a aplicacao ficar disponivel.
Write-Host "Aguardando a aplicacao em $BaseUrl ..."
for ($i = 0; $i -lt 60; $i++) {
    try {
        Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 3 | Out-Null
        Write-Host "Aplicacao disponivel."
        break
    } catch {
        Start-Sleep -Seconds 2
        if ($i -eq 59) { throw "Aplicacao nao respondeu em $BaseUrl" }
    }
}

New-Item -ItemType Directory -Force -Path (Split-Path $OutFile) | Out-Null
"volume,strategy,rep,total_records,duration_ms,throughput_per_sec" | Out-File -FilePath $OutFile -Encoding utf8

foreach ($vol in $Volumes) {
    Write-Host "`n=== Carregando $vol registros ===" -ForegroundColor Cyan
    $load = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/data/load?count=$vol"
    Write-Host ("Carga concluida: {0} registros em {1:N0} ms" -f $load.count, $load.loadMs)

    foreach ($strat in $Strategies) {
        # Execucao de aquecimento (descartada) para estabilizar JIT, cache e pool.
        Write-Host "  [$strat] warm-up..." -ForegroundColor DarkGray
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/benchmark/run?strategy=$strat&chunkSize=$ChunkSize" | Out-Null

        for ($rep = 1; $rep -le $Reps; $rep++) {
            $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/benchmark/run?strategy=$strat&chunkSize=$ChunkSize"
            $line = "{0},{1},{2},{3},{4:F2},{5:F2}" -f `
                $vol, $strat, $rep, $r.totalRecords, $r.durationMs, $r.throughputPerSec
            Add-Content -Path $OutFile -Value $line
            Write-Host ("  [$strat] rep {0}/{1}: {2:N0} ms | {3:N0} reg/s" -f `
                $rep, $Reps, $r.durationMs, $r.throughputPerSec)
        }
    }
}

Write-Host "`nResultados salvos em $OutFile" -ForegroundColor Green
Write-Host "Gere o resumo com: python analyze_results.py"
