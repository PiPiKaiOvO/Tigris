export interface Step {
  title: string;
  body?: string;
  cmd?: string;
  note?: string;
}

export interface FaqItem {
  q: string;
  a: string[];
  cmds?: { text: string }[];
}
