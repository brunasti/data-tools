# DB Structure Comparison

| | |
|---|---|
| **Source** | `receipthandler` on `10.7.1.22:5432` |
| **Target** | `receipthandler` on `10.7.1.18:5432` |
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

### `queue`

#### Columns only in TARGET

| Column | Type | Nullable | Default |
|---|---|---|---|
| `doorstuur_idf` | character varying(255) | YES | _null_ |

### `queue_overview_dto`

#### Columns only in SOURCE

| Column | Type | Nullable | Default |
|---|---|---|---|
| `first_day` | date | YES | _null_ |
| `first_day_quantity` | integer(32) | YES | _null_ |

#### Columns only in TARGET

| Column | Type | Nullable | Default |
|---|---|---|---|
| `doorstuur_idf` | character varying(255) | YES | _null_ |

## Identical Tables

- `global_config`
- `idf_info`
- `pharmacy`
- `pharmacy_special_zindexes`
- `receipt`
- `receipt_line`
- `special_zindex`
- `special_zindex_pharmacies`

