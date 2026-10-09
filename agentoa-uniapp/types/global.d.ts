interface ImportMeta {
  readonly env: Record<string, string | undefined>;
}

declare const process: {
  env: Record<string, string | undefined>;
};
