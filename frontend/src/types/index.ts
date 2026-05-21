export interface User {
  id: string;
  username: string;
  displayName: string;
  email?: string;
  bio?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface Post {
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

export interface Follow {
  id: string;
  username: string;
}

export interface Notification {
  recipientId: string;
  actorId: string;
  postId: string;
  type: "LIKE" | "REPLY";
  receivedAt?: string;
}

export interface ApiError {
  error: string;
  details: string;
}

export interface TimelineResponse {
  data: RawPost[];
}

export interface PostsResponse {
  message: string;
  data: RawPost[];
}

export interface UserResponse {
  message: string;
  data: RawUser;
}

export interface FollowsResponse {
  message: string;
  data: RawFollow[];
}

// バックエンドのレスポンス形式（テーブル名/カラム名）
export interface RawUser {
  "users/id": string;
  "users/username": string;
  "users/display_name": string;
  "users/email"?: string;
  "users/bio"?: string;
  "users/created_at": string;
  "users/updated_at"?: string;
}

export interface RawPost {
  id?: string;
  content?: string;
  post_created_at?: string;
  author_id?: string;
  author_name?: string;
  "author-id"?: string;
  "author-name"?: string;
  "users/author_id"?: string;
  "users/author_name"?: string;
  "users/author-name"?: string;
  username?: string;
  display_name?: string;
  liked_at?: string;
  "posts/id": string;
  "posts/content": string;
  "posts/created_at": string;
  "posts/user_id": string;
  "posts/parent_id"?: string | null;
  "posts/likes_count"?: number;
  "posts/replies_count"?: number;
  "users/username": string;
  "users/display_name"?: string;
  "users/bio"?: string;
  is_liked?: boolean;
  "likes-count"?: number;
}

export interface RawFollow {
  "users/id": string;
  "users/username": string;
}
