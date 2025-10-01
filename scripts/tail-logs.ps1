<#
Tail logs for stirling and postgres containers
Usage:
  .\scripts\tail-logs.ps1          # tails both containers
  .\scripts\tail-logs.ps1 stirling  # tail only stirling
#>
param(
    [string[]] $Services = @('stirling','stirling-postgres')
)

foreach ($s in $Services) {
    Write-Host "[INFO] Tailing logs for $s"
}

# Run docker compose logs -f for given services
$dockerArgs = @('compose','-f','docker-compose.postgres.yml','logs','-f') + $Services
Start-Process -FilePath 'docker' -ArgumentList $dockerArgs -NoNewWindow
