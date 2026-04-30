# DB Structure Comparison

| | |
|---|---|
| **Source** | `receipthandler` on `10.7.1.18:5432` |
| **Target** | `receipthandler-heteren` on `10.7.1.18:5432` |
| **Schemas** | `public` |
| **Date** | 2026-04-30 |

---

## Summary

| Category | Count |
|---|---|
| Tables only in SOURCE | 0 |
| Tables only in TARGET | 0 |
| Tables with differences | 2 |
| Identical tables | 8 |

---

## Table Differences

### `pharmacy`

#### Columns only in TARGET

| Column | Type | Nullable | Default |
|---|---|---|---|
| `bestelagbcode` | character varying(255) | YES | _null_ |

### `receipt`

#### Index differences

| Index | Side | Definition |
|---|---|---|
| `receipt_queue_id_index` | TARGET | `CREATE INDEX receipt_queue_id_index ON public.receipt USING btree (queue_id) INCLUDE (queue_id) WITH (deduplicate_items='true')` |

## Identical Tables

- `global_config`
- `idf_info`
- `pharmacy_special_zindexes`
- `queue`
- `queue_overview_dto`
- `receipt_line`
- `special_zindex`
- `special_zindex_pharmacies`

