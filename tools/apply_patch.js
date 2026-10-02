// The patch engine. Run by .github/workflows/apply-patch.yml whenever a patch
// file is added to patches/ (or by hand from the Actions tab).
//
//   node tools/apply_patch.js
//
// HOW IT WORKS
//   Every file patches/*.json (top level only) is a PATCH: plain data, never
//   code. Patches are applied in file-name order. Each one is applied
//   ALL-OR-NOTHING: every operation is first simulated in memory, and only if
//   every single one matches exactly is anything written. A patch that applied
//   (or was already in) is moved to patches/applied/ so it never runs twice.
//   A patch that failed stays where it is, nothing is changed, and the reason
//   is written to tools/patch_report.txt (always, success or failure).
//
// PATCH FORMAT
//   {
//     "name":    "short human title",
//     "message": "commit message (optional)",
//     "alreadyAppliedIf": { "file": "app/...", "contains": "text" },   // optional; may be a list
//     "ops": [
//       { "op": "replace", "file": "app/...", "find": "exact text", "replace": "new text", "count": 1 },
//       { "op": "write",   "file": "app/...", "content": "whole file", "mustNotExist": true },
//       { "op": "delete",  "file": "app/...", "optional": true }
//     ]
//   }
//   * "replace" needs `find` to occur exactly `count` times (default 1).
//   * "write" creates or overwrites a whole file.
//   * Paths must be under app/, tools/ or patches/, or be a top-level *.md.
//     Anything under .github/ is refused (workflow files are changed by hand).
//
// Exit code 0 = everything pending applied (or nothing pending);
//           1 = a patch failed (see tools/patch_report.txt).

const fs = require("fs");
const path = require("path");

const PATCH_DIR = "patches";
const APPLIED_DIR = path.join(PATCH_DIR, "applied");
const REPORT = "tools/patch_report.txt";
const ALLOWED_PREFIXES = ["app/", "tools/", "patches/"];

const lines = [];
function say(s) { console.log(s); lines.push(s); }

function checkPath(p) {
  if (typeof p !== "string" || !p) throw new Error("missing file path");
  if (p.includes("..") || p.includes("\\") || path.isAbsolute(p)) throw new Error("unsafe path: " + p);
  if (p.startsWith(".github/")) throw new Error("workflow files cannot be patched: " + p);
  const ok = ALLOWED_PREFIXES.some(a => p.startsWith(a)) || /^[A-Za-z0-9_.-]+\.md$/.test(p);
  if (!ok) throw new Error("path not allowed: " + p);
  return p;
}

function diskRead(p) { return fs.existsSync(p) ? fs.readFileSync(p, "utf8") : null; }

// Simulates one patch in memory. Returns { errors, state } where state maps
// path -> new text (or null for "delete"). Nothing touches the disk here.
function simulate(patch, read = diskRead) {
  const errors = [];
  const state = new Map();
  const get = p => (state.has(p) ? state.get(p) : read(p));

  if (!patch || !Array.isArray(patch.ops) || patch.ops.length === 0) {
    return { errors: ["patch has no \"ops\" list"], state };
  }
  patch.ops.forEach((op, i) => {
    const tag = `op ${i + 1} (${op && op.op} ${op && op.file})`;
    try {
      const file = checkPath(op.file);
      if (op.op === "replace") {
        if (typeof op.find !== "string" || op.find === "" || typeof op.replace !== "string") throw new Error("needs string \"find\" (non-empty) and \"replace\"");
        const text = get(file);
        if (text === null) throw new Error("file does not exist");
        const want = op.count === undefined ? 1 : op.count;
        const found = text.split(op.find).length - 1;
        if (found !== want) {
          throw new Error(`expected ${want} match(es) of the text to replace, found ${found}. Text starts: ${JSON.stringify(op.find.slice(0, 90))}`);
        }
        state.set(file, text.split(op.find).join(op.replace));
      } else if (op.op === "write") {
        if (typeof op.content !== "string") throw new Error("needs string \"content\"");
        if (op.mustNotExist && get(file) !== null) throw new Error("file already exists");
        state.set(file, op.content);
      } else if (op.op === "delete") {
        if (get(file) === null) {
          if (!op.optional) throw new Error("file does not exist");
        } else state.set(file, null);
      } else {
        throw new Error("unknown op \"" + op.op + "\"");
      }
    } catch (e) {
      errors.push(`${tag}: ${e.message}`);
    }
  });
  return { errors, state };
}

function alreadyApplied(patch, read = diskRead) {
  const guards = [].concat(patch.alreadyAppliedIf || []);
  return guards.length > 0 && guards.every(g => {
    const t = read(checkPath(g.file));
    return t !== null && t.includes(g.contains);
  });
}

function commit(state) {
  for (const [file, text] of state) {
    if (text === null) { if (fs.existsSync(file)) fs.unlinkSync(file); continue; }
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, text);
  }
}

function markApplied(file) {
  fs.mkdirSync(APPLIED_DIR, { recursive: true });
  let dest = path.join(APPLIED_DIR, path.basename(file));
  if (fs.existsSync(dest)) dest = dest.replace(/\.json$/, "") + "." + Date.now() + ".json";
  fs.renameSync(file, dest);
}

function writeReport(extra) {
  fs.mkdirSync(path.dirname(REPORT), { recursive: true });
  const head = [
    "Patch run: " + (process.env.GITHUB_RUN_ID || "local"),
    "Timestamp (UTC): " + new Date().toISOString().replace(/\.\d+Z$/, "Z"),
    extra,
    "-----"
  ];
  fs.writeFileSync(REPORT, head.concat(lines).join("\n") + "\n");
}

function setOutput(name, value) {
  if (process.env.GITHUB_OUTPUT) fs.appendFileSync(process.env.GITHUB_OUTPUT, `${name}=${value}\n`);
}

function main() {
  const pending = fs.existsSync(PATCH_DIR)
    ? fs.readdirSync(PATCH_DIR).filter(f => f.endsWith(".json") && fs.statSync(path.join(PATCH_DIR, f)).isFile()).sort()
    : [];
  if (pending.length === 0) {
    say("No pending patches.");
    writeReport("Result: nothing to do");
    setOutput("changed", "false");
    return 0;
  }

  const messages = [];
  let changed = false;
  for (const name of pending) {
    const file = path.join(PATCH_DIR, name);
    say(`Patch ${name}`);
    let patch;
    try { patch = JSON.parse(fs.readFileSync(file, "utf8")); }
    catch (e) { say("  FAILED: not valid JSON: " + e.message); writeReport("Result: FAILED at " + name); setOutput("changed", String(changed)); return 1; }

    try {
      if (alreadyApplied(patch)) {
        say("  already applied - skipped");
        markApplied(file);
        changed = true; // the move itself needs committing
        continue;
      }
    } catch (e) { say("  FAILED: " + e.message); writeReport("Result: FAILED at " + name); setOutput("changed", String(changed)); return 1; }

    const { errors, state } = simulate(patch);
    if (errors.length) {
      say("  FAILED - nothing was changed:");
      errors.forEach(e => say("    " + e));
      writeReport("Result: FAILED at " + name);
      setOutput("changed", String(changed));
      return 1;
    }
    commit(state);
    markApplied(file);
    changed = true;
    messages.push(patch.message || patch.name || name);
    say(`  applied ${patch.ops.length} operation(s) to ${state.size} file(s)`);
  }

  writeReport("Result: OK");
  setOutput("changed", String(changed));
  setOutput("message", (messages.join("; ") || "Apply patches").replace(/[\r\n]+/g, " ").slice(0, 200));
  return 0;
}

if (require.main === module) process.exit(main());
module.exports = { simulate, alreadyApplied, checkPath };
