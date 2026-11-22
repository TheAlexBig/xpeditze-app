#!/usr/bin/env bash
set -Eeuo pipefail

step_name="startup"
trap 'printf "FAILED: %s (line %s)\n" "$step_name" "$LINENO" >&2' ERR

step() {
  step_name="$1"
  printf '\n==> %s\n' "$step_name"
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

need() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing command: $1"
}

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
app_dir="$(cd -- "$script_dir/.." && pwd)"
workspace_dir="$(cd -- "$app_dir/.." && pwd)"
forms_dir="$workspace_dir/xpeditze-forms"
app_repo="TheAlexBig/xpeditze-app"
forms_repo="TheAlexBig/xpeditze-forms"
workflow="windows-installer.yml"
bump="${1:-}"

if [[ ! "$bump" =~ ^(patch|minor|major)$ ]] || [[ $# -ne 1 ]]; then
  printf 'Usage: %s patch|minor|major\n' "${0##*/}" >&2
  exit 2
fi

step "Preflight"
for command_name in gh git node npm; do
  need "$command_name"
done
[[ -d "$forms_dir/.git" ]] || fail "Missing repository: $forms_dir"
gh auth status >/dev/null

for repo_dir in "$app_dir" "$forms_dir"; do
  [[ "$(git -C "$repo_dir" branch --show-current)" == "main" ]] ||
    fail "$(basename "$repo_dir") must be on main"
  [[ -z "$(git -C "$repo_dir" status --porcelain)" ]] ||
    fail "$(basename "$repo_dir") has uncommitted changes"
  git -C "$repo_dir" fetch --quiet origin main
  [[ "$(git -C "$repo_dir" rev-parse HEAD)" == "$(git -C "$repo_dir" rev-parse origin/main)" ]] ||
    fail "$(basename "$repo_dir") main is not synchronized with origin/main"
done

app_version="$(node -e '
const text = require("fs").readFileSync(process.argv[1], "utf8");
const match = text.match(/<artifactId>xpeditze<\/artifactId>\s*<version>([^<]+)<\/version>/);
if (!match) process.exit(1);
process.stdout.write(match[1]);
' "$app_dir/pom.xml")"
forms_version="$(node -p 'require(process.argv[1]).version' "$forms_dir/package.json")"
lock_version="$(node -p 'require(process.argv[1]).version' "$forms_dir/package-lock.json")"
lock_root_version="$(node -p 'require(process.argv[1]).packages[""].version' "$forms_dir/package-lock.json")"
[[ "$app_version" == "$forms_version" && "$app_version" == "$lock_version" && "$app_version" == "$lock_root_version" ]] ||
  fail "Versions differ: app=$app_version forms=$forms_version lock=$lock_version lock-root=$lock_root_version"
[[ "$app_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] ||
  fail "Invalid current version: $app_version"

latest_tag="$(gh api "repos/$app_repo/releases/latest" --jq .tag_name)"
[[ "$latest_tag" == "v$app_version" ]] ||
  fail "Latest release $latest_tag does not match source v$app_version"

IFS=. read -r major minor patch <<<"$app_version"
case "$bump" in
  patch) patch=$((10#$patch + 1)) ;;
  minor) minor=$((10#$minor + 1)); patch=0 ;;
  major) major=$((10#$major + 1)); minor=0; patch=0 ;;
esac
next_version="$major.$minor.$patch"
branch="release/v$next_version"

for repo_dir in "$app_dir" "$forms_dir"; do
  if git -C "$repo_dir" show-ref --verify --quiet "refs/heads/$branch" ||
    git -C "$repo_dir" show-ref --verify --quiet "refs/remotes/origin/$branch"; then
    fail "Branch already exists in $(basename "$repo_dir"): $branch"
  fi
done
if gh release view "v$next_version" --repo "$app_repo" >/dev/null 2>&1; then
  fail "Release already exists: v$next_version"
fi
printf '%s -> %s\n' "$app_version" "$next_version"

step "Version files"
git -C "$app_dir" switch --quiet -c "$branch"
git -C "$forms_dir" switch --quiet -c "$branch"
node - "$app_dir/pom.xml" "$app_version" "$next_version" <<'NODE'
const fs = require("fs");
const [path, current, next] = process.argv.slice(2);
const text = fs.readFileSync(path, "utf8");
const pattern = new RegExp(`(<artifactId>xpeditze</artifactId>\\s*<version>)${current.replace(/\./g, "\\.")}(<\\/version>)`);
const updated = text.replace(pattern, `$1${next}$2`);
if (updated === text) throw new Error("Backend version was not updated");
fs.writeFileSync(path, updated);
NODE
npm --prefix "$forms_dir" version "$next_version" --no-git-tag-version --silent >/dev/null

[[ "$(git -C "$app_dir" diff --name-only)" == "pom.xml" ]] ||
  fail "Backend changed unexpected files"
forms_changes="$(git -C "$forms_dir" diff --name-only)"
[[ "$forms_changes" == $'package-lock.json\npackage.json' ]] ||
  fail "Frontend changed unexpected files"
git -C "$app_dir" diff --check
git -C "$forms_dir" diff --check
[[ "$(node -p 'require(process.argv[1]).version' "$forms_dir/package.json")" == "$next_version" ]]
[[ "$(node -p 'require(process.argv[1]).version' "$forms_dir/package-lock.json")" == "$next_version" ]]
[[ "$(node -p 'require(process.argv[1]).packages[""].version' "$forms_dir/package-lock.json")" == "$next_version" ]]

step "Release commits"
commit_message="Bump version from $app_version to $next_version"
git -C "$app_dir" add pom.xml
git -C "$app_dir" commit --quiet -m "$commit_message"
git -C "$forms_dir" add package.json package-lock.json
git -C "$forms_dir" commit --quiet -m "$commit_message"
git -C "$forms_dir" push --quiet --set-upstream origin "$branch"
git -C "$app_dir" push --quiet --set-upstream origin "$branch"

step "Pull requests"
pr_body="Automated release version sync for v$next_version."
forms_pr="$(gh pr create --repo "$forms_repo" --base main --head "$branch" --title "Release v$next_version" --body "$pr_body")"
app_pr="$(gh pr create --repo "$app_repo" --base main --head "$branch" --title "Release v$next_version" --body "$pr_body")"
printf 'Frontend: %s\nBackend:  %s\n' "$forms_pr" "$app_pr"

step "Required checks"
for pr_url in "$forms_pr" "$app_pr"; do
  check_count=0
  for _ in {1..30}; do
    check_count="$(gh pr view "$pr_url" --json statusCheckRollup --jq '.statusCheckRollup | length' 2>/dev/null || printf 0)"
    [[ "$check_count" -gt 0 ]] && break
    sleep 2
  done
  [[ "$check_count" -gt 0 ]] || fail "No checks started for $pr_url"
done
gh pr checks "$forms_pr" --watch --fail-fast
gh pr checks "$app_pr" --watch --fail-fast

step "Merge"
(cd "$forms_dir" && gh pr merge "$forms_pr" --rebase --delete-branch)
(cd "$app_dir" && gh pr merge "$app_pr" --rebase --delete-branch)
for repo_dir in "$forms_dir" "$app_dir"; do
  git -C "$repo_dir" switch --quiet main
  git -C "$repo_dir" pull --quiet --ff-only origin main
done

step "Dispatch installer"
request_id="release-$next_version-$(date -u +%Y%m%dT%H%M%SZ)"
gh workflow run "$workflow" \
  --repo "$app_repo" \
  --ref main \
  -f "version=$next_version" \
  -f frontend_ref=main \
  -f publish_release=true \
  -f "request_id=$request_id"

run_details=""
for _ in {1..30}; do
  run_details="$(gh run list --repo "$app_repo" --workflow "$workflow" --limit 10 \
    --json databaseId,displayTitle,url \
    --jq ".[] | select(.displayTitle | endswith(\"($request_id)\")) | [.databaseId, .url] | @tsv" | head -n 1)"
  [[ -n "$run_details" ]] && break
  sleep 2
done
[[ -n "$run_details" ]] || fail "Could not find dispatched workflow ($request_id)"

run_id="${run_details%%$'\t'*}"
run_url="${run_details#*$'\t'}"

step "Installer workflow"
printf 'Workflow: %s\n' "$run_url"
gh run watch "$run_id" --repo "$app_repo" --exit-status

attestation_conclusion="$(gh run view "$run_id" --repo "$app_repo" --json jobs \
  --jq '[.jobs[].steps[] | select(.name == "Attest Windows installer provenance") | .conclusion][0]')"
[[ "$attestation_conclusion" == "success" ]] ||
  fail "Provenance attestation did not succeed (conclusion: ${attestation_conclusion:-missing})"

step "Published release"
release_tag="v$next_version"
release_url="$(gh release view "$release_tag" --repo "$app_repo" \
  --json assets,isDraft,isPrerelease,tagName,url \
  --jq "select(.tagName == \"$release_tag\" and (.isDraft | not) and (.isPrerelease | not) and any(.assets[]; .name == \"Xpeditze-$next_version.msi\") and any(.assets[]; .name == \"SHA256SUMS.txt\")) | .url")"
[[ -n "$release_url" ]] ||
  fail "Release $release_tag is missing, unpublished, or does not contain the required assets"

printf '\nRelease verified: %s\nWorkflow succeeded: %s\n' "$release_url" "$run_url"
