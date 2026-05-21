# my-sns フロントエンド仕様書

> **対象**: 文字ベースの簡易 Twitter 風 SNS（学習用）
> **技術スタック**: TypeScript + React + Vite
> **配置場所**: `study/my-sns/frontend/`
> **対応デバイス**: PC / スマートフォン（レスポンシブ）

---

## 1. 技術選定

| カテゴリ | 技術 | 理由 |
|---|---|---|
| ビルド | Vite | 高速で設定が軽量、学習コスト低い |
| UI | React 19 | コンポーネント指向で学習教材が豊富 |
| 言語 | TypeScript | 型安全、API レスポンスの型定義で効果的 |
| ルーティング | React Router v7 | SPA の画面遷移に必要十分 |
| HTTP | fetch API | 外部依存なし、シンプル |
| スタイリング | CSS Modules | コンポーネントごとにスコープが分離 |
| 状態管理 | React Context + useState | 学習用として十分、外部ライブラリ不要 |

---

## 2. 画面一覧

| # | パス | 画面名 | 認証 | 説明 |
|---|---|---|---|---|
| 1 | `/login` | ログイン | × | ユーザー名・パスワード入力 |
| 2 | `/register` | ユーザー登録 | × | 新規ユーザー作成 |
| 3 | `/` | タイムライン | ○ | ホーム画面。投稿一覧 + 新規投稿フォーム |
| 4 | `/users/:username` | プロフィール | ○ | ユーザー情報 + そのユーザーの投稿一覧 |
| 5 | `/posts/:postId` | 投稿詳細 | ○ | 投稿 + 返信一覧 + 返信フォーム |
| 6 | `/settings` | 設定 | ○ | プロフィール編集（bio、表示名など） |
| 7 | `/notifications` | 通知一覧 | ○ | 通知履歴（LIKE / REPLY） |
| 8 | `/search` | ユーザー検索 | ○ | ユーザー名による前方一致検索 |

---

## 3. 画面詳細

### 3.1 ログイン画面 (`/login`)

```
┌─────────────────────────────┐
│         my-sns              │
│                             │
│  ┌───────────────────────┐  │
│  │ ユーザー名            │  │
│  └───────────────────────┘  │
│  ┌───────────────────────┐  │
│  │ パスワード            │  │
│  └───────────────────────┘  │
│                             │
│  [ ログイン ]               │
│                             │
│  アカウントをお持ちでない方  │
│  → 新規登録                 │
│                             │
│  エラーメッセージ表示エリア  │
└─────────────────────────────┘
```

**機能**:
- `POST /login` で JWT 取得 → `localStorage` に保存
- 成功時 `/` にリダイレクト
- エラー時にメッセージ表示（「ユーザー名またはパスワードが正しくありません」）
- 「新規登録」リンクで `/register` へ遷移

**使用 API**: `POST /login`

---

### 3.2 ユーザー登録画面 (`/register`)

```
┌─────────────────────────────┐
│         新規登録             │
│                             │
│  ┌───────────────────────┐  │
│  │ ユーザー名            │  │
│  └───────────────────────┘  │
│  ┌───────────────────────┐  │
│  │ 表示名                │  │
│  └───────────────────────┘  │
│  ┌───────────────────────┐  │
│  │ メールアドレス        │  │
│  └───────────────────────┘  │
│  ┌───────────────────────┐  │
│  │ パスワード            │  │
│  └───────────────────────┘  │
│                             │
│  [ 登録 ]                   │
│                             │
│  ← ログインに戻る           │
└─────────────────────────────┘
```

**機能**:
- `POST /users` でユーザー作成
- 成功時 → 自動ログイン（`POST /login` 実行）→ `/` へ
- バリデーション: 全フィールド必須、空欄チェック
- エラー表示（`Username already exists` など）

**使用 API**: `POST /users` → `POST /login`

---

### 3.3 タイムライン画面 (`/`)

```
┌────────────────────────────────────────────┐
│ [🏠] my-sns          [🔔 3]  [@user1 ▼]  │ ← ヘッダー
├────────────────────────────────────────────┤
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ 今なにしてる？                         │ │ ← 投稿フォーム
│ │                                        │ │
│ │                        [ 投稿する ]    │ │
│ └────────────────────────────────────────┘ │
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ @user2 · User Two · 5分前             │ │ ← 投稿カード
│ │ こんにちは、my-sns です                │ │
│ │ ♡ 3    💬 2                           │ │
│ └────────────────────────────────────────┘ │
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ @user1 · User One · 10分前            │ │
│ │ テスト投稿です                         │ │
│ │ ♥ 1    💬 0                           │ │ ← ♥ = いいね済
│ └────────────────────────────────────────┘ │
│                                            │
│         [ もっと読み込む ]                  │ ← offset ページネーション
└────────────────────────────────────────────┘
```

**機能**:
- ページ読み込み時に `GET /timeline` を呼び出し
- 「もっと読み込む」ボタンで `offset` を増やして追加取得
- 新規投稿フォーム（`POST /posts`、`parent_id: ""`）
- 投稿カードのいいねアイコンクリックで `POST/DELETE /posts/:id/like` をトグル
- ユーザー名クリックでプロフィール画面へ
- 投稿内容クリックで投稿詳細画面へ
- WebSocket 接続で通知をリアルタイム受信 → ヘッダーの 🔔 バッジを更新

**使用 API**: `GET /timeline`, `POST /posts`, `POST/DELETE /posts/:id/like`

---

### 3.4 プロフィール画面 (`/users/:username`)

```
┌────────────────────────────────────────────┐
│ [←] プロフィール      [🔔 3]  [@user1 ▼]  │
├────────────────────────────────────────────┤
│                                            │
│  @user2                                    │
│  User Two                                  │
│  「Clojure 学習中です」                     │ ← bio
│                                            │
│  [ フォロー中 ] or [ フォローする ]         │ ← 自分以外に表示
│  [ 設定 ]                                  │ ← 自分のみ表示
│                                            │
│  フォロー: 5人                              │ ← クリックでリスト表示
│                                            │
├────────────────────────────────────────────┤
│  投稿一覧                                  │
│ ┌────────────────────────────────────────┐ │
│ │ @user2 · 5分前                         │ │
│ │ こんにちは                              │ │
│ └────────────────────────────────────────┘ │
│  ...                                       │
└────────────────────────────────────────────┘
```

**機能**:
- `GET /users/:username` でユーザー情報取得
- `GET /posts?username=xxx` で投稿一覧取得
- 他ユーザーの場合: フォロー/アンフォローボタン表示
- 自分の場合: 「設定」ボタンで `/settings` へ
- `GET /follows?username=xxx` でフォロー一覧表示（モーダルまたはインライン）

**使用 API**: `GET /users/:username`, `GET /posts?username=`, `GET /follows?username=`, `POST/DELETE /follows/:username`

---

### 3.5 投稿詳細画面 (`/posts/:postId`)

```
┌────────────────────────────────────────────┐
│ [←] 投稿              [🔔 3]  [@user1 ▼]  │
├────────────────────────────────────────────┤
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ @user2 · User Two                      │ │ ← 親投稿
│ │ 2026-04-28 12:00                       │ │
│ │                                        │ │
│ │ こんにちは、my-sns です                │ │
│ │                                        │ │
│ │ ♡ 3    💬 2    [🗑]                   │ │ ← 自分の投稿のみ削除可
│ └────────────────────────────────────────┘ │
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ 返信を書く...                          │ │ ← 返信フォーム
│ │                          [ 返信する ]  │ │
│ └────────────────────────────────────────┘ │
│                                            │
│ ── 返信 2件 ──                             │
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ @user1 · 3分前                         │ │
│ │ いいですね！                            │ │
│ └────────────────────────────────────────┘ │
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ @user3 · 1分前                         │ │
│ │ はじめまして                            │ │
│ └────────────────────────────────────────┘ │
└────────────────────────────────────────────┘
```

**機能**:
- 親投稿の表示（タイムラインから遷移時のデータ、または API で取得）
- `GET /posts/:postId` で返信一覧取得
- 返信フォーム（`POST /posts`、`parent_id` に親投稿の ID を設定）
- いいねトグル
- 自分の投稿の場合、削除ボタン表示（`DELETE /posts/:postId`）

**使用 API**: `GET /posts/:postId`, `POST /posts`, `POST/DELETE /posts/:id/like`, `DELETE /posts/:postId`

---

### 3.6 設定画面 (`/settings`)

```
┌────────────────────────────────────────────┐
│ [←] 設定              [🔔 3]  [@user1 ▼]  │
├────────────────────────────────────────────┤
│                                            │
│  表示名                                    │
│  ┌───────────────────────────────────────┐ │
│  │ User One                              │ │
│  └───────────────────────────────────────┘ │
│                                            │
│  自己紹介                                  │
│  ┌───────────────────────────────────────┐ │
│  │ Clojure 学習中です                    │ │
│  └───────────────────────────────────────┘ │
│                                            │
│  メールアドレス                            │
│  ┌───────────────────────────────────────┐ │
│  │ user1@example.com                     │ │
│  └───────────────────────────────────────┘ │
│                                            │
│  [ 保存する ]                              │
│                                            │
│ ─────────────────────────────────────────  │
│  [ ログアウト ]                            │
│  [ アカウント削除 ]   ← 確認ダイアログ付き  │
└────────────────────────────────────────────┘
```

**機能**:
- 現在のユーザー情報を初期値として表示
- `PUT /users` で部分更新（変更のあったフィールドのみ送信）
- ログアウト: `localStorage` からトークン削除 → `/login` へ
- アカウント削除: 確認ダイアログ → `DELETE /users` → `/login` へ

**使用 API**: `PUT /users`, `DELETE /users`

---

### 3.7 通知一覧画面 (`/notifications`)

```
┌────────────────────────────────────────────┐
│ [←] 通知              [🔔]   [@user1 ▼]  │
├────────────────────────────────────────────┤
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ 🔴 @user2 があなたの投稿にいいねしました │ │ ← 未読
│ │   「こんにちは、my-sns です」 · 5分前  │ │
│ └────────────────────────────────────────┘ │
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ 💬 @user3 があなたの投稿に返信しました  │ │ ← 未読
│ │   「テスト投稿です」 · 10分前          │ │
│ └────────────────────────────────────────┘ │
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │    @user1 があなたの投稿にいいねしました │ │ ← 既読（グレー）
│ │   「おはようございます」 · 1時間前     │ │
│ └────────────────────────────────────────┘ │
└────────────────────────────────────────────┘
```

**機能**:
- WebSocket で受信した通知を一覧表示（`NotificationContext` から取得）
- 未読は強調表示（赤丸、太字など）、既読はグレー
- 通知をクリックすると対象の投稿詳細画面へ遷移
- ヘッダーの 🔔 バッジから遷移した際に未読カウントをリセット
- ⚠️ **通知一覧取得 API は未実装**。現セッション中に受信した通知のみ表示（ページリロードで消える）
- バックエンドに `GET /notifications` が追加された際に永続化対応予定

**使用 API**: （現時点では WebSocket 経由のリアルタイム通知のみ）

---

### 3.8 ユーザー検索画面 (`/search`)

```
┌────────────────────────────────────────────┐
│ [🏠] 検索              [🔔 3]  [@user1 ▼] │
├────────────────────────────────────────────┤
│                                            │
│  ┌────────────────────────────────────┐    │
│  │ 🔍 ユーザー名を検索...            │    │
│  └────────────────────────────────────┘    │
│                                            │
│  ── 検索結果 ──                            │
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ @user2  User Two                       │ │
│ │ Clojure 学習中です                     │ │
│ │                      [ フォローする ]  │ │
│ └────────────────────────────────────────┘ │
│                                            │
│ ┌────────────────────────────────────────┐ │
│ │ @user20  User Twenty                   │ │
│ │ （bio なし）                           │ │
│ │                      [ フォロー中 ]    │ │
│ └────────────────────────────────────────┘ │
└────────────────────────────────────────────┘
```

**機能**:
- 入力欄への入力をデバウンス（300ms）して `GET /search/users?q=xxx&limit=20` を呼び出す
- ⚠️ **バックエンドに `GET /search/users` API が未実装**。フロントの準備は整えておき、バックエンド実装時に接続する
- 各ユーザー行: ユーザー名クリックでプロフィールへ遷移
- フォロー/アンフォローボタンをインラインで表示
- 自分自身は検索結果に出てもフォローボタンを非表示

**使用 API（予定）**: `GET /search/users?q=&limit=`, `POST/DELETE /follows/:username`

---

## 4. 共通コンポーネント

| コンポーネント | 説明 |
|---|---|
| `Header` | ロゴ、通知バッジ、ユーザーメニュー。全認証済みページ共通 |
| `PostCard` | 投稿1件の表示。ユーザー名、内容、時刻、いいね数、返信数 |
| `PostForm` | 投稿/返信の入力フォーム。`parent_id` を props で受け取る |
| `UserCard` | ユーザー情報1件の表示。フォロー/アンフォローボタン付き |
| `UserLink` | ユーザー名をクリックでプロフィールへ遷移するリンク |
| `ProtectedRoute` | 未認証時に `/login` へリダイレクトするラッパー |
| `NotificationToast` | WebSocket 通知のポップアップ表示 |
| `NotificationItem` | 通知一覧の1件表示。アイコン（LIKE/REPLY）、テキスト、時刻 |

---

## 5. 状態管理

### AuthContext（認証）

```typescript
interface AuthContextType {
  token: string | null;       // JWT トークン
  username: string | null;    // ログイン中のユーザー名
  userId: string | null;      // ログイン中のユーザーID (UUID)
  login: (token: string) => void;
  logout: () => void;
}
```

- JWT を `localStorage` に永続化
- トークンの payload をデコード（`user_id` を取得）
- 全コンポーネントから `useAuth()` で利用

### NotificationContext（通知 + WebSocket）

```typescript
interface NotificationContextType {
  unreadCount: number;
  notifications: Notification[];   // 現セッションで受信した通知
  addNotification: (n: Notification) => void;
  markAllAsRead: () => void;       // 通知一覧画面を開いた際に呼び出す
  clearNotifications: () => void;
}
```

WebSocket の接続・切断もこの Context 内で管理する（ログイン時に接続、ログアウト時に切断）。

---

## 6. API クライアント設計

### `api.ts` — 共通 fetch ラッパー

```typescript
const BASE_URL = "http://localhost:3030/api/v1";

async function apiFetch<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  const token = localStorage.getItem("token");
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Token ${token}` } : {}),
      ...options?.headers,
    },
  });

  if (!res.ok) {
    const error = await res.json();
    throw new ApiError(res.status, error.details);
  }

  return res.json();
}
```

---

## 7. WebSocket 通知

### 接続管理

```typescript
// ログイン後に接続、ログアウト時に切断
const ws = new WebSocket(`ws://localhost:3030/api/v1/ws?token=${token}`);

ws.onmessage = (event) => {
  const notification = JSON.parse(event.data);
  // { "recipient-id": "...", "actor-id": "...", "post-id": "...", "type": "LIKE" | "REPLY" }
  addNotification(notification);
};
```

### 通知の表示

- ヘッダーの 🔔 に未読カウントをバッジ表示
- 通知受信時にトースト（数秒で自動消去）で「@user2 があなたの投稿にいいねしました」等を表示

---

## 8. レスポンシブ対応

| ブレークポイント | レイアウト |
|---|---|
| `< 768px`（スマホ） | 1カラム、フル幅。ヘッダーは上部固定 |
| `≥ 768px`（PC） | コンテンツ幅 max 600px、中央寄せ |

Twitter のようにコンテンツ幅を固定し、中央に配置するシンプルなレイアウトとする。

---

## 9. ディレクトリ構成（案）

```
my-sns/
└── frontend/                  # ← サブディレクトリに配置
    ├── index.html
    ├── package.json
    ├── tsconfig.json
    ├── vite.config.ts
    ├── public/
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── api/
        │   └── client.ts              # fetch ラッパー + 各 API 関数
        ├── contexts/
        │   ├── AuthContext.tsx         # 認証状態
        │   └── NotificationContext.tsx # 通知状態 + WebSocket 管理
        ├── components/
        │   ├── Header.tsx
        │   ├── Header.module.css
        │   ├── PostCard.tsx
        │   ├── PostCard.module.css
        │   ├── PostForm.tsx
        │   ├── PostForm.module.css
        │   ├── UserCard.tsx
        │   ├── UserCard.module.css
        │   ├── NotificationItem.tsx
        │   ├── NotificationToast.tsx
        │   └── ProtectedRoute.tsx
        ├── pages/
        │   ├── LoginPage.tsx
        │   ├── RegisterPage.tsx
        │   ├── TimelinePage.tsx
        │   ├── ProfilePage.tsx
        │   ├── PostDetailPage.tsx
        │   ├── SettingsPage.tsx
        │   ├── NotificationsPage.tsx  # 通知一覧
        │   └── SearchPage.tsx         # ユーザー検索
        ├── types/
        │   └── index.ts               # API レスポンスの型定義
        └── styles/
            └── global.css             # 全体のリセット・共通スタイル
```

---

## 10. 型定義（主要）

```typescript
// API レスポンスのキー名は "users/username" 形式
// フロントでは変換して使用する

interface User {
  id: string;
  username: string;
  displayName: string;
  email?: string;
  bio?: string;
  createdAt: string;
  updatedAt?: string;
}

interface Post {
  id: string;
  content: string;
  createdAt: string;
  userId: string;
  parentId?: string | null;
  likesCount: number;
  repliesCount: number;
  username: string;
  displayName?: string;
  bio?: string;
  isLiked?: boolean;
}

interface Notification {
  recipientId: string;
  actorId: string;
  postId: string;
  type: "LIKE" | "REPLY";
}

interface ApiResponse<T> {
  message?: string;
  data: T;
}
```

> [!IMPORTANT]
> バックエンドのレスポンスキーは `users/username`, `posts/id` のように `テーブル名/カラム名` 形式です。
> API クライアント層で camelCase に変換するユーティリティを用意します。

---

## 11. バックエンド未実装 API（フロント実装時は Stub で対応）

| API | 対応画面 | 備考 |
|---|---|---|
| `GET /notifications` | 通知一覧 | `notifications` テーブルは存在。バックエンド追加後に接続 |
| `GET /search/users?q=&limit=` | ユーザー検索 | `search-users` 関数は存在。バックエンドにルート追加後に接続 |

---

## 確定事項

- フロントエンド配置: `study/my-sns/frontend/`
- 通知一覧画面: あり（現セッションの WebSocket 受信分のみ表示、API 追加後に永続化）
- ユーザー検索: あり（バックエンド API 追加後に接続、それまでは入力欄のみ表示）
