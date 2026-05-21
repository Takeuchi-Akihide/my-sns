# my-sns API 仕様書

> **Base URL**: `http://localhost:3030/api/v1`
> **認証方式**: JWT（`Authorization: Token <JWT>` ヘッダー）
> **Content-Type**: `application/json`

---

## API 一覧サマリー

| # | メソッド | パス | 認証 | 説明 |
|---|---|---|---|---|
| 1 | POST | `/login` | × | ログイン・JWT取得 |
| 2 | POST | `/users` | × | ユーザー登録 |
| 3 | GET | `/users/:username` | ○ | ユーザー情報取得 |
| 4 | PUT | `/users` | ○ | ユーザー情報更新 |
| 5 | DELETE | `/users` | ○ | ユーザー削除 |
| 6 | POST | `/posts` | ○ | 投稿作成（通常/返信） |
| 7 | GET | `/posts?username=` | ○ | ユーザー投稿一覧 |
| 8 | GET | `/posts/:post_id` | ○ | 投稿の返信一覧 |
| 9 | DELETE | `/posts/:post_id` | ○ | 投稿削除 |
| 10 | POST | `/posts/:post_id/like` | ○ | いいね |
| 11 | DELETE | `/posts/:post_id/like` | ○ | いいね解除 |
| 12 | POST | `/follows/:target_username` | ○ | フォロー |
| 13 | DELETE | `/follows/:target_username` | ○ | アンフォロー |
| 14 | GET | `/follows?username=` | ○ | フォロー一覧 |
| 15 | GET | `/timeline` | ○ | タイムライン取得 |
| 16 | WS | `/ws?token=` | ○ | リアルタイム通知 |

---

## 1. 認証

### POST `/login`

ユーザー名とパスワードで認証し、JWT トークンを取得する。

| 項目 | 値 |
|---|---|
| 認証 | 不要 |

**Request Body**:
```json
{ "username": "user1", "password": "password" }
```

**Response (200)**:
```json
{ "token": "<JWT_TOKEN>" }
```

**JWT Payload**: `{ "user_id": "<UUID>", "exp": <ms timestamp> }` — 有効期限 **24時間**

**Error (401)**: `{ "error": "Unauthorized", "details": "Invalid username or password" }`

> [!IMPORTANT]
> 認証ヘッダーの形式は `Authorization: Token <JWT>` です。`Bearer` ではありません。

---

## 2. ユーザー管理

### POST `/users` — ユーザー登録

認証不要。全フィールド必須。

```json
{ "username": "user1", "email": "user1@example.com", "display_name": "User One", "password": "password" }
```

**Response (201)**: `{ "message": "User added successfully", "data": { ... } }`

**Errors**: `400` — フィールド不足 / `Username already exists`

---

### GET `/users/:username` — ユーザー情報取得

認証必要。

**Response (200)**:
```json
{
  "message": "User retrieved successfully",
  "data": {
    "users/id": "<UUID>",
    "users/username": "user1",
    "users/display_name": "User One",
    "users/email": "user1@example.com",
    "users/bio": "Hello!",
    "users/password_hash": "...",
    "users/created_at": "2026-04-28T12:00:00Z",
    "users/updated_at": "2026-04-28T12:00:00Z"
  }
}
```

**Errors**: `401` — 認証なし / `404` — ユーザー不存在

---

### PUT `/users` — ユーザー情報更新（部分更新対応）

認証必要。更新したいフィールドのみ送信可。

```json
{ "bio": "Updated bio" }
```

更新可能フィールド: `username`, `email`, `display_name`, `password`, `bio`

**Response (200)**: `{ "message": "User updated successfully", "data": { ... } }`

**Errors**: `400` — フィールドなし / `Username already exists` / `401`

---

### DELETE `/users` — ユーザー削除

認証必要（自分自身のみ）。

**Response (200)**: `{ "message": "User deleted successfully", "data": "<UUID>" }`

---

## 3. 投稿

### POST `/posts` — 投稿作成

認証必要。

```json
{ "content": "投稿内容", "parent_id": "" }
```

- `parent_id`: 空文字 `""` → 通常投稿、UUID 文字列 → 返信投稿

**Response (201)**:
```json
{
  "message": "Post created successfully",
  "data": {
    "posts/id": "<UUID>",
    "posts/user_id": "<UUID>",
    "posts/parent_id": null,
    "posts/content": "投稿内容",
    "posts/likes_count": 0,
    "posts/replies_count": 0,
    "posts/created_at": "2026-04-28T12:00:00Z"
  }
}
```

**副作用（非同期）**:
- 通常投稿 → フォロワー全員のタイムライン（Redis）に追加
- 返信投稿 → 親投稿の作者に `REPLY` 通知

---

### GET `/posts?username=<username>&limit=<N>` — ユーザー投稿一覧

認証必要。

| パラメータ | 型 | 必須 | デフォルト |
|---|---|---|---|
| `username` | string | ○ | — |
| `limit` | number | × | 20 |

**Response (200)**:
```json
{
  "message": "Posts retrieved successfully",
  "data": [
    { "posts/id": "<UUID>", "posts/content": "...", "posts/created_at": "...", "users/username": "user1" }
  ]
}
```

---

### GET `/posts/:post_id?limit=<N>` — 投稿の返信一覧

認証必要。`limit` デフォルト: 20。

**Response (200)**:
```json
{
  "message": "Replies retrieved successfully",
  "data": [
    { "posts/id": "<UUID>", "posts/content": "返信", "posts/created_at": "...", "users/username": "user2" }
  ]
}
```

---

### DELETE `/posts/:post_id` — 投稿削除

認証必要。自分の投稿のみ削除可。

**Response (200)**: `{ "message": "Post deleted successfully" }`

**Errors**: `403` — 他人の投稿

---

## 4. フォロー

### POST `/follows/:target_username` — フォロー

認証必要。

**Response (201)**: `{ "message": "Followed successfully", "data": "user2" }`

### DELETE `/follows/:target_username` — アンフォロー

認証必要。

**Response (201)**: `{ "message": "Unfollowed successfully", "data": "user2" }`

### GET `/follows?username=<username>` — フォロー一覧

認証必要。指定ユーザーが**フォローしている**ユーザーの一覧を返す。

**Response (200)**:
```json
{
  "message": "Followers retrieved successfully",
  "data": [{ "users/id": "<UUID>", "users/username": "user2" }]
}
```

---

## 5. タイムライン

### GET `/timeline?limit=<N>&offset=<N>` — タイムライン取得

認証必要。自分 + フォロー中ユーザーの投稿を取得。Redis キャッシュ使用。

| パラメータ | 型 | 必須 | デフォルト |
|---|---|---|---|
| `limit` | number | × | 20 |
| `offset` | number | × | 0 |

**Response (200)**:
```json
{
  "data": [
    {
      "posts/id": "<UUID>",
      "posts/content": "...",
      "posts/created_at": "...",
      "posts/user_id": "<UUID>",
      "posts/replies_count": 3,
      "users/username": "user1",
      "users/display_name": "User One",
      "users/bio": "...",
      "is_liked": true,
      "likes-count": 5
    }
  ]
}
```

> [!NOTE]
> `likes-count` は Redis 由来。`is_liked` は自分がいいね済みかどうか。

> [!TIP]
> README にはカーソルページネーションの記載がありますが、現在の実装は **offset ベース**です。

---

## 6. いいね

### POST `/posts/:post_id/like` — いいね

認証必要。

**Response (200)**: `{ "message": "Liked successfully" }` or `{ "message": "Already liked" }`

**副作用**: 投稿作者に `LIKE` 通知（自分自身には通知しない）、Redis カウンター増加

### DELETE `/posts/:post_id/like` — いいね解除

認証必要。

**Response (200)**: `{ "message": "Unliked successfully" }` or `{ "message": "Not liked" }`

**副作用**: DB から通知削除、Redis カウンター減少

---

## 7. WebSocket 通知

### GET `/ws?token=<JWT>`

WebSocket 接続でリアルタイム通知を受信する。

**接続URL**: `ws://localhost:3030/api/v1/ws?token=<JWT_TOKEN>`

**受信メッセージ（JSON）**:
```json
{ "recipient-id": "<UUID>", "actor-id": "<UUID>", "post-id": "<UUID>", "type": "LIKE" }
```

| type | 発火条件 |
|---|---|
| `LIKE` | 自分の投稿がいいねされた時 |
| `REPLY` | 自分の投稿に返信があった時 |

**通知フロー**:
```
REST API → Event Worker (core.async) → Redis PUBLISH → Pub/Sub Listener → WebSocket送信
```

---

## 8. 共通エラーレスポンス

```json
{ "error": "<Error Type>", "details": "<詳細メッセージ>" }
```

| ステータス | error | 主なケース |
|---|---|---|
| 400 | Bad Request | パラメータ不正、UUID不正、必須項目不足 |
| 401 | Unauthorized | 認証なし、トークン無効/期限切れ、アカウント削除済み |
| 403 | Forbidden | 他人の投稿を削除 |
| 404 | Not Found | ユーザー/投稿が存在しない |
| 500 | Internal Server Error | 想定外のサーバーエラー |

---

## 9. データベーススキーマ

### users
| カラム | 型 | 制約 |
|---|---|---|
| id | UUID | PK, DEFAULT gen_random_uuid() |
| username | VARCHAR(50) | UNIQUE, NOT NULL |
| display_name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| password_hash | TEXT | NOT NULL |
| bio | TEXT | nullable |
| created_at | TIMESTAMPTZ | DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMPTZ | DEFAULT CURRENT_TIMESTAMP |

### posts
| カラム | 型 | 制約 |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | FK → users(id) ON DELETE CASCADE |
| parent_id | UUID | FK → posts(id) ON DELETE CASCADE, nullable |
| content | TEXT | NOT NULL |
| likes_count | INTEGER | DEFAULT 0 |
| replies_count | INTEGER | DEFAULT 0 |
| created_at | TIMESTAMPTZ | DEFAULT CURRENT_TIMESTAMP |

### follows
| カラム | 型 | 制約 |
|---|---|---|
| follower_id | UUID | FK → users(id), PK |
| followed_id | UUID | FK → users(id), PK |
| created_at | TIMESTAMPTZ | DEFAULT CURRENT_TIMESTAMP |

### post_likes
| カラム | 型 | 制約 |
|---|---|---|
| user_id | UUID | FK → users(id) ON DELETE CASCADE, PK |
| post_id | UUID | FK → posts(id) ON DELETE CASCADE, PK |
| created_at | TIMESTAMPTZ | DEFAULT CURRENT_TIMESTAMP |

### notifications
| カラム | 型 | 制約 |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | FK → users(id) ON DELETE CASCADE |
| actor_id | UUID | FK → users(id) ON DELETE CASCADE |
| post_id | UUID | FK → posts(id) ON DELETE CASCADE, nullable |
| type | VARCHAR(50) | NOT NULL (`LIKE`, `REPLY`) |
| is_read | BOOLEAN | DEFAULT FALSE |
| created_at | TIMESTAMPTZ | DEFAULT CURRENT_TIMESTAMP |

**ユニーク制約**: `(user_id, actor_id, post_id, type) WHERE is_read = FALSE` — 未読の同一通知は UPSERT
