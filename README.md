# data-tools

A Java library of command-line tools for managing and migrating data between databases.

**Package:** `it.brunasti.dataTools`
**Build:** Maven · Java 21
**Author:** Paolo Brunasti

---

## Table of Contents

- [Overview](#overview)
- [Project Structure](#project-structure)
- [Building](#building)
- [Tools](#tools)
  - [MigrateData](#migratedata)
- [Configuration File Format](#configuration-file-format)
- [Running MigrateData](#running-migratedata)
- [Order of Tables and Foreign Keys](#order-of-tables-and-foreign-keys)
- [Design Notes](#design-notes)

---

## Overview

`data-tools` is a collection of utilities designed to automate common data management tasks.
The first tool in the collection, **MigrateData**, copies a defined set of tables from one
PostgreSQL database to another — for example, from a production database to a staging or test
environment.

---

## Project Structure

```
data-tools/
├── pom.xml
├── README.md
├── docs/
│   └── migrate-config-example.json        # Example migration configuration
└── src/
    └── main/
        ├── java/it/brunasti/dataTools/
        │   ├── package-info.java
        │   ├── BaseExecutor.java           # Base class managing output stream
        │   ├── BaseMain.java               # Base class with shared CLI helpers
        │   ├── MigrateData.java            # Migration engine
        │   ├── MigrateDataMain.java        # CLI entry point for MigrateData
        │   └── utils/
        │       ├── DbConnectionUtils.java  # JDBC connection factory
        │       ├── JsonDbRecord.java       # Model: one database connection
        │       └── JsonMigrateConfig.java  # Model: full migration configuration
        └── resources/
            └── log4j2.properties           # Logging configuration
```

---

## Building

Requires Java 21 and Maven 3.x.

```bash
cd data-tools

# Compile only
mvn compile

# Package a self-contained JAR (includes all dependencies)
mvn package
```

The assembly plugin produces a fat JAR at:

```
target/data-tools-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## Tools

### MigrateData

Copies a list of tables from a **source** database to a **target** database.
The tables are migrated in the exact order they appear in the configuration file,
which allows you to control foreign-key dependency order manually.

Optionally, each target table can be cleaned (all rows deleted) before the new
data is inserted, giving you a clean, reproducible copy of the source.

---

## Configuration File Format

The tool is driven by a single JSON file. Create one file per migration scenario
(e.g. one for prod → staging, one for prod → local-dev).

```json
{
  "source": {
    "name":     "Production",
    "server":   "prod.db.example.com",
    "port":     "5432",
    "db_name":  "myapp_prod",
    "login":    "readonly_user",
    "password": "secret"
  },
  "target": {
    "name":     "Staging",
    "server":   "staging.db.example.com",
    "port":     "5432",
    "db_name":  "myapp_staging",
    "login":    "admin_user",
    "password": "secret"
  },
  "tables": [
    "categories",
    "products",
    "customers",
    "orders",
    "order_lines"
  ]
}
```

### Fields

| Section | Field | Description |
|---------|-------|-------------|
| `source` / `target` | `name` | Human-readable label (used in log output) |
| | `server` | Hostname or IP of the PostgreSQL server |
| | `port` | PostgreSQL port (default: `5432`) |
| | `db_name` | Name of the database to connect to |
| | `login` | Database user name |
| | `password` | Database user password |
| `tables` | *(array of strings)* | Ordered list of table names to migrate |

An example configuration file is provided at `docs/migrate-config-example.json`.

> **Security note:** Avoid committing configuration files that contain real credentials
> to version control. Add `*-prod.json`, `*-access.json`, etc. to your `.gitignore`.

---

## Running MigrateData

### Command-line options

| Option | Long form | Argument | Description |
|--------|-----------|----------|-------------|
| `-c`   | `--config` | `<file>` | Path to the JSON configuration file *(required)* |
| `-x`   | `--clean`  | —        | Delete all rows from each target table before inserting |
| `-h`   | `--help`   | —        | Print the full help message |
| `-?`   | —          | —        | Print a short usage summary |

### Examples

**Basic migration** — appends source data on top of whatever is already in the target:

```bash
java -cp target/data-tools-1.0-SNAPSHOT-jar-with-dependencies.jar \
     it.brunasti.dataTools.MigrateDataMain \
     -c docs/migrate-config-example.json
```

**Clean migration** — deletes all existing rows in the target tables first, then imports:

```bash
java -cp target/data-tools-1.0-SNAPSHOT-jar-with-dependencies.jar \
     it.brunasti.dataTools.MigrateDataMain \
     -c docs/migrate-config-example.json \
     -x
```

**Help:**

```bash
java -cp target/data-tools-1.0-SNAPSHOT-jar-with-dependencies.jar \
     it.brunasti.dataTools.MigrateDataMain \
     -h
```

### Example output

```
=== MigrateData ===
Source : Production / myapp_prod
Target : Staging / myapp_staging
Tables : 5
Clean  : true
-------------------
  Table [categories] ... 42 rows migrated.
  Table [products] ... 1387 rows migrated.
  Table [customers] ... 9204 rows migrated.
  Table [orders] ... 28541 rows migrated.
  Table [order_lines] ... 94312 rows migrated.
-------------------
Migration COMPLETED successfully.
```

### Exit codes

| Code | Meaning |
|------|---------|
| `0`  | All tables migrated successfully |
| `1`  | One or more tables failed; check the log output |

---

## Order of Tables and Foreign Keys

The tables in the `"tables"` array are migrated **strictly in order**, from top to bottom.

This is intentional: relational databases enforce foreign-key constraints, so a child table
(e.g. `order_lines`) cannot be populated until its parent table (e.g. `orders`) already exists
in the target. By controlling the order in the configuration file, you control when each table
is loaded.

**Rule of thumb:** list tables from the most independent (no foreign keys) to the most
dependent (references other tables).

```json
"tables": [
  "categories",      ← no FK dependencies
  "products",        ← references categories
  "customers",       ← no FK dependencies
  "orders",          ← references customers
  "order_lines"      ← references orders and products
]
```

If the target database enforces FK constraints and the order is wrong, the migration of the
offending table will fail with a constraint-violation error. The error is logged and reported,
and the tool continues with the remaining tables.

---

## Design Notes

- **Batch inserts:** rows are written to the target in batches of 500 using JDBC
  `addBatch` / `executeBatch` with `autoCommit=false`, then committed at the end.
  This gives good performance without loading the entire table into memory at once.

- **Type handling:** column values are transferred using `ResultSet.getObject()` and
  `PreparedStatement.setObject()`, which delegates type mapping to the JDBC driver.
  This handles all standard SQL types (text, integer, boolean, timestamp, UUID, JSON, etc.)
  without explicit type conversion code.

- **Two-phase execution with `-x`:** when cleaning is requested the tool runs two
  distinct phases. Phase 1 deletes all target tables in **reverse** configuration
  order (child tables before parent tables) to satisfy FK constraints. Phase 2
  then populates all tables in **forward** order so that parent rows exist before
  child rows are inserted.

- **Clean uses DELETE, not TRUNCATE:** `DELETE FROM table` is used instead of
  `TRUNCATE … CASCADE`. `TRUNCATE CASCADE` can silently cascade to tables outside
  the configured list, potentially deleting data you did not intend to touch.

- **Programmatic use:** `MigrateDataMain.exec(configFile, clean)` can be called from
  other Java code without going through the CLI.
