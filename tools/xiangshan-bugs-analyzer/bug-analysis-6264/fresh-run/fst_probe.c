#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "/usr/local/share/verilator/include/gtkwave/fstapi.h"

typedef struct {
  fstHandle handle;
  uint32_t length;
  char *name;
} Sig;

typedef struct {
  Sig *items;
  size_t count;
  size_t cap;
} SigList;

typedef struct {
  char **items;
  size_t count;
  size_t cap;
} ScopeStack;

static int contains_all(const char *name, int n, char **subs) {
  for (int i = 0; i < n; i++) {
    if (!strstr(name, subs[i])) {
      return 0;
    }
  }
  return 1;
}

static void push_sig(SigList *list, fstHandle handle, uint32_t length, const char *name) {
  if (list->count == list->cap) {
    size_t next = list->cap ? list->cap * 2 : 256;
    Sig *items = (Sig *)realloc(list->items, next * sizeof(Sig));
    if (!items) {
      perror("realloc");
      exit(1);
    }
    list->items = items;
    list->cap = next;
  }
  list->items[list->count].handle = handle;
  list->items[list->count].length = length;
  list->items[list->count].name = strdup(name);
  if (!list->items[list->count].name) {
    perror("strdup");
    exit(1);
  }
  list->count++;
}

static void free_sigs(SigList *list) {
  for (size_t i = 0; i < list->count; i++) {
    free(list->items[i].name);
  }
  free(list->items);
}

static void push_scope(ScopeStack *stack, const char *name) {
  if (stack->count == stack->cap) {
    size_t next = stack->cap ? stack->cap * 2 : 64;
    char **items = (char **)realloc(stack->items, next * sizeof(char *));
    if (!items) {
      perror("realloc");
      exit(1);
    }
    stack->items = items;
    stack->cap = next;
  }
  stack->items[stack->count] = strdup(name ? name : "");
  if (!stack->items[stack->count]) {
    perror("strdup");
    exit(1);
  }
  stack->count++;
}

static void pop_scope(ScopeStack *stack) {
  if (stack->count == 0) {
    return;
  }
  stack->count--;
  free(stack->items[stack->count]);
}

static void free_scopes(ScopeStack *stack) {
  while (stack->count) {
    pop_scope(stack);
  }
  free(stack->items);
}

static void make_full_name(ScopeStack *stack, const char *leaf, char *out, size_t out_sz) {
  out[0] = '\0';
  for (size_t i = 0; i < stack->count; i++) {
    if (i != 0) {
      strncat(out, ".", out_sz - strlen(out) - 1);
    }
    strncat(out, stack->items[i], out_sz - strlen(out) - 1);
  }
  if (out[0]) {
    strncat(out, ".", out_sz - strlen(out) - 1);
  }
  strncat(out, leaf ? leaf : "", out_sz - strlen(out) - 1);
}

static SigList collect(fstReaderContext *ctx, int nsubs, char **subs) {
  SigList list = {0};
  ScopeStack stack = {0};
  struct fstHier *h;
  fstReaderIterateHierRewind(ctx);
  while ((h = fstReaderIterateHier(ctx)) != NULL) {
    if (h->htyp == FST_HT_SCOPE) {
      push_scope(&stack, h->u.scope.name);
    } else if (h->htyp == FST_HT_UPSCOPE) {
      pop_scope(&stack);
    } else if (h->htyp == FST_HT_VAR) {
      char full[8192];
      make_full_name(&stack, h->u.var.name, full, sizeof(full));
      if (contains_all(full, nsubs, subs)) {
        push_sig(&list, h->u.var.handle, h->u.var.length, full);
      }
    }
  }
  free_scopes(&stack);
  return list;
}

static int cmp_sig(const void *a, const void *b) {
  const Sig *sa = (const Sig *)a;
  const Sig *sb = (const Sig *)b;
  return strcmp(sa->name, sb->name);
}

static void usage(const char *argv0) {
  fprintf(stderr,
          "usage:\n"
          "  %s <fst> list <substring> [substring...]\n"
          "  %s <fst> sample <time> <substring> [substring...]\n",
          argv0, argv0);
}

int main(int argc, char **argv) {
  if (argc < 4) {
    usage(argv[0]);
    return 2;
  }

  const char *fst = argv[1];
  const char *mode = argv[2];
  fstReaderContext *ctx = fstReaderOpen(fst);
  if (!ctx) {
    fprintf(stderr, "failed to open %s\n", fst);
    return 1;
  }

  if (strcmp(mode, "list") == 0) {
    SigList sigs = collect(ctx, argc - 3, argv + 3);
    qsort(sigs.items, sigs.count, sizeof(Sig), cmp_sig);
    for (size_t i = 0; i < sigs.count; i++) {
      printf("%u %u %s\n", sigs.items[i].handle, sigs.items[i].length, sigs.items[i].name);
    }
    free_sigs(&sigs);
  } else if (strcmp(mode, "sample") == 0) {
    if (argc < 5) {
      usage(argv[0]);
      fstReaderClose(ctx);
      return 2;
    }
    char *end = NULL;
    uint64_t time = strtoull(argv[3], &end, 0);
    if (!end || *end) {
      fprintf(stderr, "bad time: %s\n", argv[3]);
      fstReaderClose(ctx);
      return 2;
    }
    SigList sigs = collect(ctx, argc - 4, argv + 4);
    qsort(sigs.items, sigs.count, sizeof(Sig), cmp_sig);
    char *buf = (char *)malloc(1 << 20);
    if (!buf) {
      perror("malloc");
      free_sigs(&sigs);
      fstReaderClose(ctx);
      return 1;
    }
    for (size_t i = 0; i < sigs.count; i++) {
      char *val = fstReaderGetValueFromHandleAtTime(ctx, time, sigs.items[i].handle, buf);
      printf("#%" PRIu64 " %u %u %s %s\n", time, sigs.items[i].handle, sigs.items[i].length,
             sigs.items[i].name, val ? val : "<null>");
    }
    free(buf);
    free_sigs(&sigs);
  } else {
    usage(argv[0]);
    fstReaderClose(ctx);
    return 2;
  }

  fstReaderClose(ctx);
  return 0;
}
