# Batch Benchmark — JPA vs SQL Nativo

Ambiente experimental do TCC **"Desempenho de Processamentos em Lote em Bancos
Relacionais com Spring Boot e JPA"**. Compara estratégias de acesso a dados em
processamento batch sob carga controlada, com coleta de métricas via
Micrometer → Prometheus → Grafana.

## Arquitetura

| Componente | Tecnologia | Porta |
|---|---|---|
| Aplicação | Java 21 + Spring Boot 3.3 | 8080 |
| Banco de dados | PostgreSQL 16 | 5432 |
| Coleta de métricas | Prometheus | 9090 |
| Visualização | Grafana | 3000 |

Tudo roda em contêineres Docker isolados, com limites de CPU/memória definidos no
`docker-compose.yml` para garantir comparabilidade entre execuções.

## Estratégias avaliadas

| `strategy` | Descrição |
|---|---|
| `jpa` | ORM via Hibernate; entidades gerenciadas, UPDATEs agrupados por `hibernate.jdbc.batch_size`. |
| `native` | **SQL nativo com batch update** (foco do estudo) — `JdbcTemplate.batchUpdate`, sem ORM. |
| `native-bulk` | *(bônus)* UPDATE set-based único — teto de desempenho declarativo. |

A operação medida atualiza todos os registros (`valor = valor_base * 1,10`,
`processado = true`). É **determinística e repetível**: cada repetição parte do
mesmo estado lógico, sem deriva entre execuções.

## Pré-requisitos

- Docker + Docker Compose (modo recomendado — não exige Java/Maven local)
- PowerShell (para o script de execução) e Python 3 (para a análise)

## Passo a passo

### 1. Subir o ambiente

```powershell
cd batch-benchmark
docker compose up -d --build
```

Aguarde ~1–2 min no primeiro build. Verifique:

```powershell
docker compose ps
curl http://localhost:8080/actuator/health
```

### 2. Executar os experimentos

```powershell
./run-experiments.ps1
```

O script carrega os dados, faz uma execução de aquecimento (descartada) e roda as
repetições para cada volume e estratégia, gravando `results/results.csv`.

Parâmetros opcionais:

```powershell
./run-experiments.ps1 -Volumes 100000,500000,1000000 -Strategies jpa,native -Reps 10 -ChunkSize 1000
```

### 3. Analisar os resultados

```powershell
python analyze_results.py
```

Gera `results/results_summary.csv` (média, desvio-padrão, ganho relativo) e imprime
tabelas em Markdown prontas para colar na seção **Resultados Preliminares** do TCC.

### 4. Visualizar no Grafana

Acesse <http://localhost:3000> (admin / admin). O dashboard
**"Batch Benchmark - JPA vs SQL Nativo"** já vem provisionado, com painéis de
duração, throughput, uso de CPU e memória heap. Use as capturas como figuras no TCC.

### 5. Encerrar

```powershell
docker compose down        # mantém os dados no volume
docker compose down -v     # remove também o volume do banco
```

## Endpoints da API

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/data/load?count=100000` | Trunca e recarrega N registros |
| GET  | `/api/data/count` | Conta registros |
| POST | `/api/benchmark/run?strategy=jpa&chunkSize=1000` | Executa e retorna métricas |
| GET  | `/actuator/prometheus` | Métricas para o Prometheus |

## Rodando a aplicação localmente (sem Docker para o app)

Suba apenas a infraestrutura e rode o app via Maven:

```powershell
docker compose up -d postgres prometheus grafana
mvn spring-boot:run
```

> Observação: nesse modo, ajuste o alvo do Prometheus em
> `monitoring/prometheus/prometheus.yml` de `app:8080` para
> `host.docker.internal:8080` para que ele alcance o app no host.

## Observações metodológicas

- **Warm-up**: a primeira execução de cada estratégia é descartada (estabiliza JIT,
  pool de conexões e cache do banco).
- **Repetições**: por padrão 5 por cenário; aumente (`-Reps 10/30`) para maior robustez estatística.
- **Variáveis controladas**: pool de conexões, `chunkSize`, limites de CPU/memória e
  volume de dados são fixados entre execuções.
- **reWriteBatchedInserts=true**: habilita o agrupamento real dos comandos na camada
  do driver PostgreSQL — essencial para uma comparação justa de batching.
