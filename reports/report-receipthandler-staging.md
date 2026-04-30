# DB Structure Comparison

| | |
|---|---|
| **Source** | `receipthandler` on `10.7.1.22:5432` |
| **Target** | `receipthandler-heteren` on `10.7.1.22:5432` |
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

#### Columns only in TARGET

| Column | Type | Nullable | Default |
|---|---|---|---|
| `bestelagbcode` | character varying(255) | YES | _null_ |

### `queue`

#### Columns only in TARGET

| Column | Type | Nullable | Default |
|---|---|---|---|
| `doorstuur_idf` | character varying(255) | YES | _null_ |

### `queue_overview_dto`

#### Columns only in TARGET

| Column | Type | Nullable | Default |
|---|---|---|---|
| `doorstuur_idf` | character varying(255) | YES | _null_ |

#### Column property differences

| Column | Property | SOURCE | TARGET |
|---|---|---|---|
| `first_day` | position | 13 | 14 |
| `first_day_quantity` | position | 14 | 15 |

## Identical Tables

- `global_config`
- `idf_info`
- `pharmacy_special_zindexes`
- `receipt`
- `receipt_line`
- `special_zindex`
- `special_zindex_pharmacies`

