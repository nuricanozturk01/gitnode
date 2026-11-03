export type DiscussionCategory = 'ANNOUNCEMENTS' | 'GENERAL' | 'IDEAS' | 'Q_AND_A' | 'SHOW_AND_TELL';

export const DISCUSSION_CATEGORIES: { value: DiscussionCategory; label: string; hint: string }[] = [
  { value: 'ANNOUNCEMENTS', label: 'Announcements', hint: 'Updates from the maintainers' },
  { value: 'GENERAL', label: 'General', hint: 'Anything about the project' },
  { value: 'IDEAS', label: 'Ideas', hint: 'Suggest and discuss new features' },
  { value: 'Q_AND_A', label: 'Q&A', hint: 'Ask the community for help' },
  { value: 'SHOW_AND_TELL', label: 'Show and tell', hint: 'Share what you built' },
];

export interface DiscussionAuthor {
  name: string | null;
  email: string;
  username: string;
  avatarUrl: string | null;
}

export interface DiscussionInfo {
  number: number;
  title: string;
  category: DiscussionCategory;
  author: DiscussionAuthor;
  commentCount: number;
  upvotes: number;
  answered: boolean;
  locked: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface DiscussionComment {
  id: string;
  parentId: string | null;
  author: DiscussionAuthor;
  body: string;
  isAnswer: boolean;
  replyCount: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface DiscussionDetail {
  info: DiscussionInfo;
  body: string | null;
  answer: DiscussionComment | null;
  viewerUpvoted: boolean;
  canModerate: boolean;
}

export interface DiscussionPage<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DiscussionListFilter {
  category: DiscussionCategory | '';
  unanswered: boolean;
  sort: 'latest' | 'updated';
  page: number;
}
