import type {
  User,
  Post,
  Follow,
  RawUser,
  RawPost,
  RawFollow,
  UserResponse,
  PostsResponse,
  TimelineResponse,
  FollowsResponse,
} from "../types";

const BASE_URL = "http://localhost:3030/api/v1";

export class ApiError extends Error {
  status: number;
  details: string;

  constructor(status: number, details: string) {
    super(details);
    this.status = status;
    this.details = details;
  }
}

function getToken(): string | null {
  return localStorage.getItem("token");
}

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getToken();
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Token ${token}` } : {}),
      ...options?.headers,
    },
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({ details: res.statusText }));
    throw new ApiError(res.status, body.details ?? body.error ?? res.statusText);
  }

  return res.json() as Promise<T>;
}

// --- 変換ユーティリティ ---

function normalizeUser(raw: RawUser): User {
  return {
    id: raw["users/id"],
    username: raw["users/username"],
    displayName: raw["users/display_name"],
    email: raw["users/email"],
    bio: raw["users/bio"],
    createdAt: raw["users/created_at"],
    updatedAt: raw["users/updated_at"],
  };
}

function readRaw(raw: RawPost, keys: string[]): string | undefined {
  const record = raw as unknown as Record<string, unknown>;
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.length > 0) return value;
  }
  return undefined;
}

function normalizePost(raw: RawPost): Post {
  const authorName = readRaw(raw, [
    "users/username",
    "author_name",
    "author-name",
    "users/author_name",
    "users/author-name",
    "username",
  ]);
  const authorId = readRaw(raw, [
    "posts/user_id",
    "author_id",
    "author-id",
    "users/author_id",
    "users/author-id",
  ]);

  return {
    id: raw["posts/id"] ?? raw.id ?? "",
    content: raw["posts/content"] ?? raw.content ?? "",
    createdAt: raw["posts/created_at"] ?? raw.post_created_at ?? raw.liked_at ?? "",
    userId: authorId ?? "",
    parentId: raw["posts/parent_id"],
    likesCount: raw["likes-count"] ?? raw["posts/likes_count"] ?? 0,
    repliesCount: raw["posts/replies_count"] ?? 0,
    username: authorName ?? "",
    displayName: raw["users/display_name"] ?? raw.display_name ?? authorName,
    bio: raw["users/bio"],
    isLiked: raw["is_liked"] ?? false,
  };
}

function normalizeFollow(raw: RawFollow): Follow {
  return {
    id: raw["users/id"],
    username: raw["users/username"],
  };
}

// --- 認証 ---

export async function login(
  username: string,
  password: string
): Promise<{ token: string; username?: string; user_id?: string }> {
  return apiFetch("/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

// --- ユーザー ---

export async function registerUser(
  username: string,
  displayName: string,
  email: string,
  password: string
): Promise<void> {
  await apiFetch("/users", {
    method: "POST",
    body: JSON.stringify({ username, display_name: displayName, email, password }),
  });
}

export async function getUser(username: string): Promise<User> {
  const res = await apiFetch<UserResponse>(`/users/${encodeURIComponent(username)}`);
  return normalizeUser(res.data);
}

export async function getCurrentUser(): Promise<User> {
  const res = await apiFetch<UserResponse>("/users");
  return normalizeUser(res.data);
}

export async function updateUser(updates: {
  username?: string;
  email?: string;
  display_name?: string;
  password?: string;
  bio?: string;
}): Promise<void> {
  await apiFetch("/users", {
    method: "PUT",
    body: JSON.stringify(updates),
  });
}

export async function deleteUser(): Promise<void> {
  await apiFetch("/users", { method: "DELETE" });
}

// --- 投稿 ---

export async function createPost(
  content: string,
  parentId?: string
): Promise<void> {
  await apiFetch("/posts", {
    method: "POST",
    body: JSON.stringify({ content, parent_id: parentId ?? "" }),
  });
}

export async function listPosts(username: string, limit = 20): Promise<Post[]> {
  const res = await apiFetch<PostsResponse>(
    `/posts?username=${encodeURIComponent(username)}&limit=${limit}`
  );
  return res.data.map(normalizePost);
}

export async function listReplies(postId: string, limit = 20): Promise<Post[]> {
  const res = await apiFetch<PostsResponse>(`/posts/${postId}?limit=${limit}`);
  return res.data.map(normalizePost);
}

export async function deletePost(postId: string): Promise<void> {
  await apiFetch(`/posts/${postId}`, { method: "DELETE" });
}

// --- いいね ---

export async function likePost(postId: string): Promise<void> {
  await apiFetch(`/posts/${postId}/like`, { method: "POST" });
}

export async function unlikePost(postId: string): Promise<void> {
  await apiFetch(`/posts/${postId}/like`, { method: "DELETE" });
}

// --- フォロー ---

export async function follow(username: string): Promise<void> {
  await apiFetch(`/follows/${encodeURIComponent(username)}`, { method: "POST" });
}

export async function unfollow(username: string): Promise<void> {
  await apiFetch(`/follows/${encodeURIComponent(username)}`, { method: "DELETE" });
}

export async function listFollows(username: string, limit = 20, offset = 0): Promise<Follow[]> {
  const res = await apiFetch<FollowsResponse>(
    `/users/${encodeURIComponent(username)}/follows?limit=${limit}&offset=${offset}`
  );
  return res.data.map(normalizeFollow);
}

export async function listFollowers(username: string, limit = 20, offset = 0): Promise<Follow[]> {
  const res = await apiFetch<FollowsResponse>(
    `/users/${encodeURIComponent(username)}/followers?limit=${limit}&offset=${offset}`
  );
  return res.data.map(normalizeFollow);
}

// --- タイムライン ---

export async function getTimeline(limit = 20, offset = 0): Promise<Post[]> {
  const res = await apiFetch<TimelineResponse>(
    `/timeline?limit=${limit}&offset=${offset}`
  );
  return res.data.map(normalizePost);
}

export async function listLikedPosts(username: string, limit = 20, offset = 0): Promise<Post[]> {
  const res = await apiFetch<PostsResponse>(
    `/users/${encodeURIComponent(username)}/likes?limit=${limit}&offset=${offset}`
  );
  return res.data.map((post) => ({ ...normalizePost(post), isLiked: true }));
}

// --- 未実装 API (Stub) ---

export async function searchUsers(
  _q: string,
  _limit = 20
): Promise<User[]> {
  void _limit;
  // TODO: GET /search/users?q=&limit= が実装されたら接続する
  return [];
}
