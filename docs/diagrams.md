# AI Translation Service — Architecture Diagrams

Paste each block into https://www.plantuml.com/plantuml/uml/ to render.

---

## 1. HLD — System Architecture

```plantuml
@startuml hld
skinparam defaultFontName Arial
skinparam defaultFontSize 13
skinparam rectangleBorderColor #888888
skinparam backgroundColor white
skinparam shadowing false

rectangle "Client" {
  [Chrome Extension]
}

rectangle "Backend" {
  [REST API Layer]
  [TranslateCoreService\n· cache-first lookup\n· batch partition\n· NodeParserHandler\n· ExcelParseService]
}

rectangle "Infrastructure" {
  [Redis\ntranslation cache]
  [SofaMQ (RabbitMQ-compatible)\nproducer · consumer]
  [MySQL\ntask · task_text]
  [LLM Gateway\nQwen]
  [OSS (S3-compatible)\ndoc storage]
}

[Chrome Extension] --> [REST API Layer] : JWT
[REST API Layer] --> [TranslateCoreService]
[TranslateCoreService] --> [Redis\ntranslation cache]
[TranslateCoreService] --> [SofaMQ (RabbitMQ-compatible)\nproducer · consumer]
[TranslateCoreService] --> [MySQL\ntask · task_text]
[SofaMQ (RabbitMQ-compatible)\nproducer · consumer] --> [LLM Gateway\nQwen]
[LLM Gateway\nQwen] ..> [Redis\ntranslation cache] : write result
[MySQL\ntask · task_text] --> [OSS (S3-compatible)\ndoc storage]

@enduml
```

---

## 2. Sequence Diagram — Async Translation Flow

```plantuml
@startuml sequence
skinparam defaultFontName Arial
skinparam defaultFontSize 13
skinparam sequenceArrowThickness 1
skinparam sequenceBoxBorderColor #888888
skinparam sequenceLifeLineBorderColor #aaaaaa
skinparam backgroundColor white
skinparam shadowing false

participant Client
participant "REST API" as API
participant "CoreService" as Core
participant "Redis" as Redis
participant "SofaMQ\n(RabbitMQ-compatible)" as MQ
participant "LLM Gateway\n(Qwen)" as LLM

Client -> API : POST /translate (JWT)
API -> Core : translate(request)

Core -> Redis : GET cache keys\n(SHA-256 keyed)
Redis --> Core : partial hit

note over Core : deduplicate · filter blank\ncompute uncached list\npartition into batches

Core -> MQ : enqueue batch\n(AES-256 encrypted)

== async ==

MQ -> LLM : model call\n(translate / grammar / PlantUML)
LLM --> MQ : result
MQ -> Redis : success → SET value (long TTL)\nfailure → SET sentinel NONE (short TTL)

== polling loop ==

loop until pending empty or timeout
  Core -> Redis : GET pending keys
  Redis --> Core : resolved / NONE
end

Core --> API : HandleResult\n(succeed list + failed list)
API --> Client : 200 OK

@enduml
```

---

## 3. Deployment Diagram — AWS

```plantuml
@startuml deployment
skinparam defaultFontName Arial
skinparam defaultFontSize 13
skinparam rectangleBorderColor #888888
skinparam backgroundColor white
skinparam shadowing false

rectangle "AWS Cloud" {
  rectangle "VPC" {
    rectangle "Public Subnet" {
      [Application Load Balancer]
    }
    rectangle "Private Subnet" {
      [ECS\nSpring Boot Service]
      [ElastiCache\n(Redis-compatible)]
      [Amazon SQS\n(MQ-compatible)]
      [RDS MySQL]
    }
  }
  [S3\ntranslated docs]
  [LLM Gateway\nQwen API]
}

[Browser / Extension] --> [Application Load Balancer] : HTTPS
[Application Load Balancer] --> [ECS\nSpring Boot Service]
[ECS\nSpring Boot Service] --> [ElastiCache\n(Redis-compatible)]
[ECS\nSpring Boot Service] --> [Amazon SQS\n(MQ-compatible)]
[ECS\nSpring Boot Service] --> [RDS MySQL]
[Amazon SQS\n(MQ-compatible)] --> [LLM Gateway\nQwen API]
[LLM Gateway\nQwen API] --> [ElastiCache\n(Redis-compatible)] : write result
[ECS\nSpring Boot Service] --> [S3\ntranslated docs]

@enduml
```

---

## 4. Observability

```plantuml
@startuml observability
skinparam defaultFontName Arial
skinparam defaultFontSize 13
skinparam rectangleBorderColor #888888
skinparam backgroundColor white
skinparam shadowing false

rectangle "Service Layer" {
  [TranslateCoreService]
  [SofaMQ Consumer]
  [LLM Gateway]
}

rectangle "AntMonitor" {
  [Metrics Collection]
  [Alert Rules\n· failure rate >= 10/min\n· consumer lag spike\n· LLM timeout]
}

rectangle "Key Metrics" {
  [Translation failure rate]
  [MQ consumer lag]
  [Cache hit rate]
  [LLM response latency]
  [Task pipeline duration]
}

[DingTalk\nAlert Webhook] as Ding

[TranslateCoreService] --> [Metrics Collection]
[SofaMQ Consumer] --> [Metrics Collection]
[LLM Gateway] --> [Metrics Collection]

[Metrics Collection] --> [Translation failure rate]
[Metrics Collection] --> [MQ consumer lag]
[Metrics Collection] --> [Cache hit rate]
[Metrics Collection] --> [LLM response latency]
[Metrics Collection] --> [Task pipeline duration]

[Metrics Collection] --> [Alert Rules\n· failure rate >= 10/min\n· consumer lag spike\n· LLM timeout]
[Alert Rules\n· failure rate >= 10/min\n· consumer lag spike\n· LLM timeout] --> Ding : threshold breach

@enduml
```

---

## 5. Cache Strategy

```plantuml
@startuml cache_strategy
skinparam defaultFontName Arial
skinparam defaultFontSize 13
skinparam rectangleBorderColor #888888
skinparam backgroundColor white
skinparam shadowing false

start

:Receive source text list;
:Build cache keys\nSHA-256(capability + src_locale + tgt_locale + text);

partition "Cache Lookup" {
  :GET all keys from Redis;
  if (all keys hit?) then (yes)
    :return cached results immediately;
    stop
  else (no)
    :split → cached list + uncached list;
  endif
}

partition "Failed Key Check" {
  if (failed sentinel key exists?) then (yes)
    :move to failed list\nskip LLM call;
  else (no)
    :add to to-be-translated list;
  endif
}

partition "SofaMQ Dispatch" {
  :partition into batches\n(batch size per capability type);
  :AES-256 encrypt each batch;
  :enqueue to SofaMQ;
}

partition "Async Consumer" {
  :decrypt payload;
  :call LLM (Qwen);
  if (success?) then (yes)
    :SET success key\nencrypted value · long TTL;
  else (no)
    :SET failed key\nsentinel NONE · short TTL;
  endif
}

partition "Polling Loop" {
  repeat
    :sleep(intervalMs);
    :GET pending keys from Redis;
    :remove resolved + failed from pending;
  repeat while (pending not empty AND attempts < maxRetries)
}

:merge cached + resolved → succeed list;
:merge timed-out + failed → failed list;
:return AITranslateHandleResult;

stop

note right
  Load test mode:
  cache key appended with traceId
  isolates test traffic from
  production cache slots
end note

@enduml
```
