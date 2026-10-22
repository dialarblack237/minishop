# Local setup — MiniShop

This file records the tools installed on the development machine
so any future run (or any reviewer of the repo) can reproduce them.

## Versions
- Java: 21.0.12.1 (Temurin LTS, Eclipse Adoptium)
- Maven: 3.9.6
- Kafka: 3.9.1 (KRaft mode, no ZooKeeper)
- (Redis/Memurai will be added in Lesson 3.1.3)

## Kafka (KRaft, single node)
- Install location: `C:\tools\kafka`
- Scripts: `C:\tools\kafka\bin\windows\*.bat` (Windows-specific)
- Config: `C:\tools\kafka\config\kraft\server.properties`
  - `process.roles=broker,controller`
  - `controller.quorum.voters=1@localhost:9093`
  - `log.dirs=C:/tools/kafka/data` (forward slashes — Windows path)
- Cluster id: `PjT4NY2rRh-z8j2VZBBskg`
- Bootstrap server: `localhost:9092`

## Kafka topics (v1)
- `orders` — the main pipeline topic
- `orders.DLQ` — dead-letter queue

## How to start Kafka locally
In a PowerShell window:

## Redis / Memurai
- On Windows: Memurai (Redis-compatible replacement on port 6379)
- Version: 4.1.2 (Memurai Developer)
- Service name: `Memurai` (runs as a Windows service, starts on boot)
- Port: 6379
- Client binary: `memurai-cli` (the Redis `redis-cli` equivalent)
- Verified: `memurai-cli ping` returns `PONG`

## How to check Memurai locally
```powershell
Get-Service Memurai            # should show Status: Running
memurai-cli ping               # should return PONG