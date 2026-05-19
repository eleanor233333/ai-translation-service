# AI Translation Service

A backend service for asynchronous, multi-language AI-powered translation, grammar checking, and document translation. Built for a multilingual browser extension supporting 50+ locales.

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [System Architecture](#system-architecture)
- [Core Async Flow](#core-async-flow)
- [My Contributions](#my-contributions)
- [Project Structure](#project-structure)
- [Tech Stack](#tech-stack)
- [API Design](#api-design)
- [Database Schema](#database-schema)

---

## Overview

This service powers the translation backend for a multilingual browser extension. It handles:

- **Batch text translation** — translate up to N strings per request across 50+ locales
- **Grammar checking** — LanguageTool-based and LLM-based grammar correction
- **Async document translation** — translate structured documents (`.lake`, Excel `.xlsx`) via a 5-stage message-driven pipeline
- **Glossary management** — terminology consistency enforced across all translations

The core design challenge: translation via LLM is slow and non-deterministic. The service decouples the HTTP request from the LLM call using a **message queue + Redis polling pattern**, allowing the caller to receive results without blocking on model latency.

---

## Key Features

- **Cache-first architecture** — SHA-256 keyed Redis cache checked before any MQ dispatch; cached hits returned immediately
- **Async MQ pipeline** — texts not in cache are batched and dispatched to a message queue; results written back to cache by consumers
- **Polling result collection** — main thread polls Redis at configurable intervals until all texts are resolved or timeout is reached
- **5-stage document state machine** — `CREATE → EXECUTE → CHECK → FINAL_CHECK → GENERATE`; each stage driven by a separate MQ message type
- **Pluggable node parser** — documents are parsed into typed `TranslateNode` objects; each node type (text, PlantUML diagram, draw board) has its own parser/generator handler
- **Dual cache key strategy** — success results and failure results stored under separate keys, preventing repeated LLM calls on known-bad inputs
- **Encrypted message transport** — MQ payloads encrypted with AES-256 before enqueue; decrypted by consumers before processing

---

## System Architecture

```text
┌─────────────────────────────────────────────────────┐
│                  Chrome Extension                    │
└────────────────────────┬────────────────────────────┘
                         │ JWT auth
                         ▼
┌─────────────────────────────────────────────────────┐
│                   REST API Layer                     │
│   POST /translate   POST /checkGrammar   GET /url   │
└────────────────────────┬────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────┐
│              TranslateCoreService                    │
│                                                     │
│  1. Deduplicate + filter input strings              │
│  2. Cache lookup  ──hit──▶  return immediately      │
│  3. Batch partition by capability type              │
│  4. MQ dispatch (encrypted)                         │
│  5. Poll Redis until pending list empty / timeout   │
│  6. Merge success + failed → return result          │
└───────────────┬─────────────────┬───────────────────┘
                │                 │
                ▼                 ▼
        ┌──────────┐      ┌──────────────┐
        │  Redis   │      │  MQ Consumer │
        │  Cache   │◀─────│  + LLM Call  │
        └──────────┘      └──────────────┘
```

For document translation, a separate async pipeline runs:

```text
CREATE ──▶ EXECUTE ──▶ CHECK ──▶ FINAL_CHECK ──▶ GENERATE
  │           │                                      │
parse doc   translate                           upload to
into nodes  each node                           S3 / OSS
```

---

## Core Async Flow

```text
Request received
    │
    ▼
Deduplicate · trim · filter blank strings
    │
    ▼
Redis cache lookup  (key = SHA-256(capability + source_locale + target_locale + text))
    │
    ├── all cached ──────────────────────────────────▶ return immediately
    │
    └── partial / none cached
            │
            ▼
        Compute uncached list
            │
            ▼
        Partition into batches
        (batch size configured per capability type:
         TRANSLATE / CHECK_GRAMMAR / PLANTUML each have own config)
            │
            ▼
        MQ Producer: AES-256 encrypt → enqueue each batch
            │
            ▼
        [async] MQ Consumer picks up message
            │
            ▼
        Decrypt payload → LLM model call
            │
            ├── success → write encrypted value to cache (long TTL)
            └── failure → write sentinel "NONE" to failed key (short TTL)
                │
                ▼
        [main thread polling]
        while (pending not empty AND attempts < maxRetries):
            sleep(intervalMs)
            check cache for each pending text
            move resolved texts out of pending list
                │
                ▼
        Timeout: remaining pending → failed list
            │
            ▼
        Merge success + failed → return AITranslateHandleResult
```

---

## My Contributions

### `TranslateCoreService` — core async orchestration

Implemented the core async orchestration flow, including cache-first lookup, MQ dispatch logic, polling loop, and result merging. Key areas:

- **Cache-first before MQ dispatch** — reduces queue pressure for repeated or similar requests
- **Dual key strategy** — short-TTL failure cache prevents repeated retries on invalid inputs
- **Configurable batch size per capability** — TRANSLATE / CHECK_GRAMMAR / PLANTUML each have independently tunable batch sizes

### Node Parser System — pluggable document parsing

Designed and implemented a `NodeParserHandler` abstraction for structured document translation:

```java
interface NodeParserHandler {
    List<TranslateNode> parse(String content);          // break doc into typed nodes
    String generate(String content, TranslateNode node); // write translated node back
    TranslateNodeTypeEnum nodeType();
}
```

| Handler | Responsibility |
|---|---|
| `TextNodeParserHandler` | Plain text and HTML prose |
| `PlantUmlNodeParserHandler` | PlantUML source — extracts labels, translates, reconstructs valid UML |
| `DrawBoardNodeParserHandler` | Draw board JSON — navigates structure, translates text fields |

The `TranslateNode` model captures everything needed for a round-trip:

```java
class TranslateNode {
    String content;                        // original text
    TranslateNodeContentTypeEnum contentType;
    TranslateNodeTypeEnum type;
    String translatedContent;              // written back after translation
    TranslateNodePosition position;        // start/end offset in original doc
    Map<String, String> metadata;
}
```

### `ExcelParseServiceImpl` — Excel document translation

- Extracts only `STRING`-type cells (skips formula, numeric, blank)
- Deduplicates before sending to translation
- Merges translated values back cell-by-cell using original value as lookup key

### `TranslateUtil` — cache key construction and result parsing

- `buildCacheKey()` — deterministic SHA-256 key; same text+locale always maps to same cache slot
- `parseResultContent()` — strips markdown fences from LLM output before JSON parsing
- `formatRequestList()` — partitions string list into sub-batches of configurable size

---

## Project Structure

```text
src/main/java/com/example/aitranslate/
│
├── constant/
│   ├── CapabilityTypeEnum.java
│   ├── DocMessageType.java
│   ├── LocaleEnum.java
│   └── TranslateNodeTypeEnum.java
│
├── model/
│   ├── node/
│   │   ├── TranslateNode.java
│   │   ├── TranslateNodePosition.java
│   │   ├── TranslateNodeContentTypeEnum.java
│   │   └── TranslateNodeTypeEnum.java
│   └── message/
│       ├── ModelCallMessage.java
│       ├── DocMessage.java
│       └── ResultMessage.java
│
├── service/
│   ├── TranslateService.java
│   ├── ParseService.java
│   └── NodeParserHandler.java
│
├── impl/
│   ├── TranslateCoreServiceImpl.java      ← core async flow
│   ├── TextNodeParserHandler.java         ← plain text / HTML
│   ├── PlantUmlNodeParserHandler.java     ← PlantUML diagrams
│   ├── DrawBoardNodeParserHandler.java    ← draw board JSON
│   ├── ExcelParseServiceImpl.java         ← Excel parse + merge
│   ├── CacheServiceImpl.java              ← Redis layer (team infra)
│   ├── AesEncryptServiceImpl.java         ← AES-256 encrypt/decrypt (team infra)
│   └── ModelCallServiceImpl.java          ← LLM dispatch (team infra)
│
├── message/
│   ├── TranslateProducer.java
│   ├── TranslateProducerImpl.java
│   └── TranslateConsumerImpl.java
│
└── utils/
    ├── TranslateUtil.java
    ├── JwtUtil.java
    └── PromptUtil.java
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot |
| Cache | Redis |
| Message Queue | SofaMQ (Ant Group in-house MQ, RabbitMQ-compatible interface) |
| LLM | Qwen (via abstracted model gateway) |
| Document storage | OSS (S3-compatible) |
| Excel parsing | Apache POI |
| HTML parsing | Jsoup |
| Auth | JWT (hutool) |
| Build | Maven |

---

## API Design

### `POST /api/v1/translate`

```json
// request
{
  "sourceStrings": ["Hello", "How are you"],
  "sourceLocale": "en_US",
  "targetLocale": "zh_CN"
}

// response
{
  "success": true,
  "data": {
    "succeed": [
      { "id": "Hello", "content": "你好" },
      { "id": "How are you", "content": "你好吗" }
    ],
    "failed": []
  }
}
```

### `POST /api/v1/translate/document`

```json
// request
{ "content": "<html>...", "sourceLocale": "en_US", "targetLocale": "zh_CN" }

// response
{ "success": true, "data": "c87051d4-e860-4fa2-ac7e-3d7ced075a43" }
```

### `GET /api/v1/translate/document/{taskId}`

```json
// response
{ "success": true, "data": "https://your-storage.example.com/translated/task-uuid.lake" }
```

### `POST /api/v1/grammar/check`

```json
// request
{ "sourceLocale": "en_US", "sourceStrings": ["This are wrong"] }

// response
{
  "success": true,
  "data": {
    "succeed": [
      { "id": "This are wrong", "corrections": [{ "replacement": "These are wrong", "from": 0, "length": 7 }] }
    ],
    "failed": []
  }
}
```

---

## Database Schema

```sql
CREATE TABLE translate_task (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    uuid        VARCHAR(64) NOT NULL,
    status      VARCHAR(64) NOT NULL,
    url         TEXT,
    content     TEXT,
    user_id     VARCHAR(64),
    gmt_create  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    gmt_modify  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_uuid (uuid),
    INDEX idx_uuid_user_id (uuid, user_id)
);

CREATE TABLE translate_task_text (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id          VARCHAR(64) NOT NULL,
    status           VARCHAR(64) NOT NULL,
    original_text    TEXT,
    translated_text  TEXT,
    gmt_create       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    gmt_modify       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id)
);
```
