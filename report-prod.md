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
| Tables with differences | 10 |
| Identical tables | 0 |

---

## Table Differences

### `global_config`

#### Constraint differences

| Constraint | Status | Definition |
|---|---|---|
| `2200_16636_1_not_null` | only in SOURCE | CHECK(null) |
| `2200_16636_2_not_null` | only in SOURCE | CHECK(null) |
| `2200_16753_1_not_null` | only in TARGET | CHECK(null) |
| `2200_16753_2_not_null` | only in TARGET | CHECK(null) |

### `idf_info`

#### Constraint differences

| Constraint | Status | Definition |
|---|---|---|
| `2200_16644_1_not_null` | only in SOURCE | CHECK(null) |
| `2200_16644_2_not_null` | only in SOURCE | CHECK(null) |
| `2200_16644_3_not_null` | only in SOURCE | CHECK(null) |
| `2200_16644_4_not_null` | only in SOURCE | CHECK(null) |
| `2200_16644_7_not_null` | only in SOURCE | CHECK(null) |
| `2200_16644_8_not_null` | only in SOURCE | CHECK(null) |
| `2200_16644_9_not_null` | only in SOURCE | CHECK(null) |
| `2200_16761_1_not_null` | only in TARGET | CHECK(null) |
| `2200_16761_2_not_null` | only in TARGET | CHECK(null) |
| `2200_16761_3_not_null` | only in TARGET | CHECK(null) |
| `2200_16761_4_not_null` | only in TARGET | CHECK(null) |
| `2200_16761_7_not_null` | only in TARGET | CHECK(null) |
| `2200_16761_8_not_null` | only in TARGET | CHECK(null) |
| `2200_16761_9_not_null` | only in TARGET | CHECK(null) |

### `pharmacy`

#### Columns only in TARGET

| Column | Type | Nullable | Default |
|---|---|---|---|
| `bestelagbcode` | character varying(255) | YES | _null_ |

#### Constraint differences

| Constraint | Status | Definition |
|---|---|---|
| `2200_16651_1_not_null` | only in SOURCE | CHECK(null) |
| `2200_16651_2_not_null` | only in SOURCE | CHECK(null) |
| `2200_16651_3_not_null` | only in SOURCE | CHECK(null) |
| `2200_16768_1_not_null` | only in TARGET | CHECK(null) |
| `2200_16768_2_not_null` | only in TARGET | CHECK(null) |
| `2200_16768_3_not_null` | only in TARGET | CHECK(null) |

### `pharmacy_special_zindexes`

#### Constraint differences

| Constraint | Status | Definition |
|---|---|---|
| `2200_16659_1_not_null` | only in SOURCE | CHECK(null) |
| `2200_16659_2_not_null` | only in SOURCE | CHECK(null) |
| `2200_16776_1_not_null` | only in TARGET | CHECK(null) |
| `2200_16776_2_not_null` | only in TARGET | CHECK(null) |

### `queue`

#### Constraint differences

| Constraint | Status | Definition |
|---|---|---|
| `2200_16664_1_not_null` | only in SOURCE | CHECK(null) |
| `2200_16664_2_not_null` | only in SOURCE | CHECK(null) |
| `2200_16664_3_not_null` | only in SOURCE | CHECK(null) |
| `2200_16664_7_not_null` | only in SOURCE | CHECK(null) |
| `2200_16781_1_not_null` | only in TARGET | CHECK(null) |
| `2200_16781_2_not_null` | only in TARGET | CHECK(null) |
| `2200_16781_3_not_null` | only in TARGET | CHECK(null) |
| `2200_16781_7_not_null` | only in TARGET | CHECK(null) |

### `queue_overview_dto`

#### Constraint differences

| Constraint | Status | Definition |
|---|---|---|
| `2200_16671_1_not_null` | only in SOURCE | CHECK(null) |
| `2200_16788_1_not_null` | only in TARGET | CHECK(null) |

### `receipt`

#### Index differences

| Index | Status | Definition |
|---|---|---|
| `receipt_queue_id_index` | only in TARGET | CREATE INDEX receipt_queue_id_index ON public.receipt USING btree (queue_id) INCLUDE (queue_id) WITH (deduplicate_items='true') |

#### Constraint differences

| Constraint | Status | Definition |
|---|---|---|
| `2200_16678_17_not_null` | only in SOURCE | CHECK(null) |
| `2200_16678_1_not_null` | only in SOURCE | CHECK(null) |
| `2200_16678_20_not_null` | only in SOURCE | CHECK(null) |
| `2200_16678_25_not_null` | only in SOURCE | CHECK(null) |
| `2200_16678_2_not_null` | only in SOURCE | CHECK(null) |
| `2200_16678_35_not_null` | only in SOURCE | CHECK(null) |
| `2200_16678_3_not_null` | only in SOURCE | CHECK(null) |
| `2200_16678_4_not_null` | only in SOURCE | CHECK(null) |
| `2200_16678_5_not_null` | only in SOURCE | CHECK(null) |
| `2200_16795_17_not_null` | only in TARGET | CHECK(null) |
| `2200_16795_1_not_null` | only in TARGET | CHECK(null) |
| `2200_16795_20_not_null` | only in TARGET | CHECK(null) |
| `2200_16795_25_not_null` | only in TARGET | CHECK(null) |
| `2200_16795_2_not_null` | only in TARGET | CHECK(null) |
| `2200_16795_35_not_null` | only in TARGET | CHECK(null) |
| `2200_16795_3_not_null` | only in TARGET | CHECK(null) |
| `2200_16795_4_not_null` | only in TARGET | CHECK(null) |
| `2200_16795_5_not_null` | only in TARGET | CHECK(null) |

### `receipt_line`

#### Constraint differences

| Constraint | Status | Definition |
|---|---|---|
| `2200_16685_14_not_null` | only in SOURCE | CHECK(null) |
| `2200_16685_1_not_null` | only in SOURCE | CHECK(null) |
| `2200_16685_20_not_null` | only in SOURCE | CHECK(null) |
| `2200_16685_21_not_null` | only in SOURCE | CHECK(null) |
| `2200_16685_9_not_null` | only in SOURCE | CHECK(null) |
| `2200_16802_14_not_null` | only in TARGET | CHECK(null) |
| `2200_16802_1_not_null` | only in TARGET | CHECK(null) |
| `2200_16802_20_not_null` | only in TARGET | CHECK(null) |
| `2200_16802_21_not_null` | only in TARGET | CHECK(null) |
| `2200_16802_9_not_null` | only in TARGET | CHECK(null) |

### `special_zindex`

#### Constraint differences

| Constraint | Status | Definition |
|---|---|---|
| `2200_16692_1_not_null` | only in SOURCE | CHECK(null) |
| `2200_16692_2_not_null` | only in SOURCE | CHECK(null) |
| `2200_16692_3_not_null` | only in SOURCE | CHECK(null) |
| `2200_16692_4_not_null` | only in SOURCE | CHECK(null) |
| `2200_16809_1_not_null` | only in TARGET | CHECK(null) |
| `2200_16809_2_not_null` | only in TARGET | CHECK(null) |
| `2200_16809_3_not_null` | only in TARGET | CHECK(null) |
| `2200_16809_4_not_null` | only in TARGET | CHECK(null) |

### `special_zindex_pharmacies`

#### Constraint differences

| Constraint | Status | Definition |
|---|---|---|
| `2200_16699_1_not_null` | only in SOURCE | CHECK(null) |
| `2200_16699_2_not_null` | only in SOURCE | CHECK(null) |
| `2200_16816_1_not_null` | only in TARGET | CHECK(null) |
| `2200_16816_2_not_null` | only in TARGET | CHECK(null) |

