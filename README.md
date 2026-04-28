# my-sns

`my-sns` は、一般的な SNS のバックエンドを学習用に2週間で実装するプロジェクトです。  

実装技術の中心は以下です。

- Clojure / Leiningen
- Ring / Reitit
- PostgreSQL
- `next.jdbc`
- `buddy` によるパスワードハッシュ化と JWT 認証

## 現在の主な機能

- ユーザー登録
- ログインと JWT 発行
- ユーザー情報の取得、更新、削除
- 投稿作成、削除
- 投稿一覧取得
- 投稿へのいいね / 解除
- 投稿への返信一覧取得
- フォロー / アンフォロー
- タイムライン取得

## 前提環境

- Docker / Docker Compose
- Java
- Leiningen
- `psql` コマンドを使う場合は PostgreSQL クライアント

## DB の立ち上げ

プロジェクトルートで PostgreSQL を起動します。`mysns_db` と `mysns_test_db` の両方が作成されます。

```bash
cd my-sns
docker compose up -d
```

起動確認:

```bash
docker compose ps
```

## psql で DB を確認する方法
DB設定は以下です。

- host: `localhost`
- port: `5432`
- database: `mysns_db` (開発用), `mysns_test_db` (テスト用)
- user: `dev`
- password: `password`

ローカルの `psql` から接続する場合:

```bash
psql -h localhost -p 5432 -U dev -d mysns_db
```

パスワード入力を求められたら `password` を入力してください。

コンテナ内から接続する場合:

```bash
docker exec -it mysns-postgres psql -U dev -d mysns_db
```

接続後の確認例:

```sql
\dt
SELECT * FROM users;
SELECT * FROM posts;
SELECT * FROM follows;
SELECT * FROM post_likes;
```

## サーバー起動方法

別ターミナルでアプリを起動します。

```bash
cd my-sns
lein run server
```

初回起動時に `schema/create-schema!` が実行されるため、必要なテーブルは自動作成されます。  
デフォルトでは `http://localhost:3030` で起動します。

ポートは環境変数 `PORT` で変更できます。

```bash
PORT=4040 lein run server
```

DB 接続先を変更したい場合は `DATABASE_URL` を指定できます。

## 利用可能なコマンド

- `lein run server`: サーバー起動
- `lein run recreate`: DB スキーマの再作成 (開発用)
- `lein run load-sample`: サンプルデータの投入 (開発用)
- `./scripts/dev-seed-activity.sh`: dev server に投稿・返信・いいねを流す

```bash
DATABASE_URL='jdbc:postgresql://localhost:5432/mysns_db?user=dev&password=password' lein run server
```

スキーマを作り直したい場合:

```bash
lein run recreate
```

`load-sample` でユーザーとフォロー関係を投入したあとに、投稿・返信・いいねをまとめて流す場合:

```bash
lein run load-sample
lein run server
./scripts/dev-seed-activity.sh
```

別ポートや別ホストでサーバーを起動している場合:

```bash
BASE_URL=http://localhost:4040 ./scripts/dev-seed-activity.sh
```

一定間隔で流し続ける場合:

```bash
LOOP_MODE=1 INTERVAL_SECONDS=10 ./scripts/dev-seed-activity.sh
```

5回だけ流したい場合:

```bash
LOOP_MODE=1 ITERATIONS=5 INTERVAL_SECONDS=3 ./scripts/dev-seed-activity.sh
```

## API 実行例

ベース URL:

```text
http://localhost:3030/api/v1
```

### 1. ユーザー登録

```bash
curl -X POST http://localhost:3030/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user1",
    "email": "user1@example.com",
    "display_name": "User One",
    "password": "password"
  }'
```

### 2. ログイン

```bash
curl -X POST http://localhost:3030/api/v1/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user1",
    "password": "password"
  }'
```

レスポンス例:

```json
{"token":"<JWT_TOKEN>"}
```

以降の認証が必要な API ではこのトークンを使います。

```bash
export TOKEN='<JWT_TOKEN>'
```

### 3. 指定したユーザー情報を取得

```bash
curl http://localhost:3030/api/v1/users/user1 \
  -H "Authorization: Token $TOKEN"
```

### 4. ユーザー情報を更新

全フィールドの更新：

```bash
curl -X PUT http://localhost:3030/api/v1/users \
  -H "Authorization: Token $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user1",
    "email": "user1@example.com",
    "display_name": "User One Updated",
    "password": "new-password",
    "bio": "I am learning Clojure."
  }'
```

部分更新（`bio` のみ更新）:

```bash
curl -X PUT http://localhost:3030/api/v1/users \
  -H "Authorization: Token $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "bio": "Updated bio"
  }'
```

### 5. 投稿を作成

通常投稿:

```bash
curl -X POST http://localhost:3030/api/v1/posts \
  -H "Authorization: Token $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "content": "こんにちは、my-sns です",
    "parent_id": ""
  }'
```

返信投稿:

```bash
curl -X POST http://localhost:3030/api/v1/posts \
  -H "Authorization: Token $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "content": "これは返信です",
    "parent_id": "<PARENT_POST_ID>"
  }'
```

### 6. 特定ユーザーの投稿一覧を取得

```bash
curl "http://localhost:3030/api/v1/posts?username=user1&limit=20" \
  -H "Authorization: Token $TOKEN"
```

### 7. 投稿の返信一覧を取得

```bash
curl "http://localhost:3030/api/v1/posts/<POST_ID>?limit=20" \
  -H "Authorization: Token $TOKEN"
```

### 8. 投稿を削除

```bash
curl -X DELETE http://localhost:3030/api/v1/posts/<POST_ID> \
  -H "Authorization: Token $TOKEN"
```

### 9. フォローする

```bash
curl -X POST http://localhost:3030/api/v1/follows/user2 \
  -H "Authorization: Token $TOKEN"
```

### 10. フォロー一覧を取得

```bash
curl "http://localhost:3030/api/v1/follows?username=user1" \
  -H "Authorization: Token $TOKEN"
```

現在の実装では、この API は「指定ユーザーがフォローしているユーザー一覧」を返します。

### 11. アンフォローする

```bash
curl -X DELETE http://localhost:3030/api/v1/follows/user2 \
  -H "Authorization: Token $TOKEN"
```

### 12. タイムラインを取得

```bash
curl "http://localhost:3030/api/v1/timeline?limit=20" \
  -H "Authorization: Token $TOKEN"
```

タイムラインには自分とフォロー中ユーザーの親投稿が含まれ、`like_count`、`reply_count`、`is_liked` を返します。

ページネーションには `cursor_date` と `cursor_id` を使います。
次ページを取得するには、前回のレスポンスに含まれる `meta.next_cursor_date` と `meta.next_cursor_id` を指定します。

```bash
curl "http://localhost:3030/api/v1/timeline?limit=20&cursor_date=<NEXT_CURSOR_DATE>&cursor_id=<NEXT_CURSOR_ID>" \
  -H "Authorization: Token $TOKEN"
```

レスポンス例:

```json
{
  "data": [ ... ],
  "meta": {
    "has_next": true,
    "next_cursor_date": "2026-04-24T12:00:00.000Z",
    "next_cursor_id": "..."
  }
}
```

### 13. 投稿にいいねする

```bash
curl -X POST http://localhost:3030/api/v1/posts/<POST_ID>/like \
  -H "Authorization: Token $TOKEN"
```

### 14. 投稿のいいねを解除する

```bash
curl -X DELETE http://localhost:3030/api/v1/posts/<POST_ID>/like \
  -H "Authorization: Token $TOKEN"
```

### 15. ユーザーを削除

```bash
curl -X DELETE http://localhost:3030/api/v1/users \
  -H "Authorization: Token $TOKEN"
```

## 作成されるテーブル

- `users`
- `posts`
- `follows`
- `post_likes`

## 補足

- 認証には JWT を使用しています。
- `Authorization` ヘッダは `Bearer` ではなく `Token <JWT>` の形式で送っています。
- ユーザー情報の部分更新に対応しており、更新したいフィールドのみを送信できます。
- エラーレスポンスの精度が向上し、ユーザーアカウント削除後のリトライガイダンスが改善されました。
- 例外処理は一部実装途中で、未分類のエラーは `500 Internal Server Error` になります。
- テストコードはまだ雛形段階です。
