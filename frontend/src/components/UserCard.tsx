import { Link } from "react-router-dom";
import type { User } from "../types";
import styles from "./UserCard.module.css";

interface Props {
  user: User;
  isFollowing: boolean;
  isMe: boolean;
  onFollowToggle: (username: string, isFollowing: boolean) => void;
}

export default function UserCard({ user, isFollowing, isMe, onFollowToggle }: Props) {
  return (
    <div className={styles.card}>
      <div className={styles.info}>
        <Link to={`/users/${user.username}`} className={styles.username}>
          @{user.username}
        </Link>
        <span className={styles.displayName}>{user.displayName}</span>
        {user.bio && <p className={styles.bio}>{user.bio}</p>}
      </div>
      {!isMe && (
        <button
          className={`${styles.followBtn} ${isFollowing ? styles.following : ""}`}
          onClick={() => onFollowToggle(user.username, isFollowing)}
        >
          {isFollowing ? "フォロー中" : "フォローする"}
        </button>
      )}
    </div>
  );
}
