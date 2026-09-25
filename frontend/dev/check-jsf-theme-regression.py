"""JSF non-regression check for the PrimeReact aliasing: compares two compiled (expanded) CSS files.
Every rule of OLD must exist in NEW with identical declarations and a superset of its selectors,
every selector NEW adds must mention a .p- class, and every rule only in NEW must be .p- only."""
import re, sys
from collections import defaultdict

def split_top(sel):
    out, depth, cur = [], 0, ''
    for ch in sel:
        if ch == '(': depth += 1
        elif ch == ')': depth -= 1
        if ch == ',' and depth == 0:
            out.append(cur); cur = ''
        else:
            cur += ch
    out.append(cur)
    return [o.strip() for o in out]

def norm(sel):
    # @extend also rewrites :not() lists (":not(.ui-widget)" -> ":not(.ui-widget):not(.p-component)" or
    # ":not(.a, .p-b)"). For markup without .p-* classes (all JSF markup) that is the same selector, so
    # compare with the .p-* alternatives removed from :not().
    sel = re.sub(r':not\((\.p-[\w-]+(?:\s*,\s*\.p-[\w-]+)*)\)', '', sel)
    def strip_list(m):
        parts = [x.strip() for x in split_top(m.group(1))]
        keep = [x for x in parts if not re.fullmatch(r'\.p-[\w-]+', x)]
        return ':not(' + ', '.join(keep) + ')'
    return re.sub(r':not\(((?:[^()]|\([^()]*\))*)\)', strip_list, sel.strip())

def parse(text):
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    rules = []  # (context, selectors tuple, decls)
    i, n, stack = 0, len(text), []
    buf = ''
    while i < n:
        c = text[i]
        if c == '{':
            head = buf.strip(); buf = ''
            if head.startswith('@') and not head.startswith('@font-face') and not head.startswith('@page'):
                stack.append(head)
                i += 1; continue
            # rule: read to matching }
            depth, j = 1, i + 1
            while depth:
                if text[j] == '{': depth += 1
                elif text[j] == '}': depth -= 1
                j += 1
            body = text[i+1:j-1]
            decls = tuple(sorted(d.strip() for d in body.split(';') if d.strip()))
            sels = tuple(norm(x) for x in split_top(head) if x.strip())
            rules.append((' | '.join(stack), sels, decls))
            i = j; continue
        if c == '}':
            if stack: stack.pop()
            buf = ''; i += 1; continue
        if c == ';' and buf.strip().startswith('@'):
            buf = ''; i += 1; continue
        buf += c; i += 1
    return rules

old = parse(open(sys.argv[1]).read())
new = parse(open(sys.argv[2]).read())
index = defaultdict(list)
for ctx, sels, decls in new:
    index[(ctx, decls)].append(set(sels))
used = set()
missing, bad_added = [], []
for ctx, sels, decls in old:
    cands = index.get((ctx, decls), [])
    hit = next((c for c in cands if set(sels) <= c), None)
    if hit is None:
        missing.append((ctx, sels[:3], decls[:3])); continue
    for extra in hit - set(sels):
        if '.p-' not in extra and extra not in sels:
            bad_added.append((ctx, extra))
old_keys = {(ctx, decls) for ctx, _, decls in old}
new_only_bad = []
for ctx, sels, decls in new:
    if (ctx, decls) in old_keys: continue
    for s in sels:
        if '.p-' not in s:
            new_only_bad.append((ctx, s, decls[:2]))
print(f"old rules: {len(old)}  new rules: {len(new)}")
print(f"old rules missing/changed in new: {len(missing)}")
for m in missing[:15]: print("   MISSING", m)
print(f"added selectors without .p-: {len(bad_added)}")
for b in bad_added[:15]: print("   BAD-ADDED", b)
print(f"new-only rules with a non-.p- selector: {len(new_only_bad)}")
for b in new_only_bad[:15]: print("   BAD-NEW", b)
sys.exit(1 if (missing or bad_added or new_only_bad) else 0)
