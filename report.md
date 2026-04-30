# DB Structure Comparison

| | |
|---|---|
| **Source** | `receipthandler-heteren` on `10.7.1.22:5432` |
| **Target** | `receipthandler-heteren` on `10.7.1.18:5432` |
| **Schemas** | `public` |
| **Date** | 2026-04-30 |

---

## Summary

| Category | Count |
|---|---|
| Tables only in SOURCE | 0 |
| Tables only in TARGET | 0 |
| Tables with differences | 3 |
| Identical tables | 7 |

---

## Table Differences

### `pharmacy`

#### Column property differences

| Column | Property | SOURCE | TARGET |
|---|---|---|---|
| `bestelagbcode` | position | 4 | 8 |

### `queue_overview_dto`

#### Columns only in SOURCE

| Column | Type | Nullable | Default |
|---|---|---|---|
| `first_day` | date | YES | _null_ |
| `first_day_quantity` | integer(32) | YES | _null_ |

### `receipt`

#### Index differences

| Index | Status | Definition |
|---|---|---|
| `receipt_queue_id_index` | only in TARGET | CREATE INDEX receipt_queue_id_index ON public.receipt USING btree (queue_id) INCLUDE (queue_id) WITH (deduplicate_items='true') |

## Identical Tables

- `global_config`
- `idf_info`
- `pharmacy_special_zindexes`
- `queue`
- `receipt_line`
- `special_zindex`
- `special_zindex_pharmacies`

